"""
Internal queue service using asyncio and threading
"""
import asyncio
import logging
import threading
import time
import uuid
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass, field
from datetime import datetime
from enum import Enum
from queue import Queue, Empty
from typing import Dict, Any, Optional, Callable, List
from app.config import settings

logger = logging.getLogger(__name__)


class TaskStatus(str, Enum):
    """Task status enumeration"""
    PENDING = "pending"
    PROCESSING = "processing" 
    COMPLETED = "completed"
    FAILED = "failed"


@dataclass
class Task:
    """Task data class"""
    task_id: str
    task_type: str
    data: Dict[str, Any]
    status: TaskStatus = TaskStatus.PENDING
    created_at: datetime = field(default_factory=datetime.now)
    started_at: Optional[datetime] = None
    completed_at: Optional[datetime] = None
    result: Optional[Dict[str, Any]] = None
    error: Optional[str] = None
    progress: int = 0


class InternalQueueService:
    """Internal queue service for background task processing"""
    
    def __init__(self, max_workers: int = 4):
        """
        Initialize queue service
        
        Args:
            max_workers: Maximum number of worker threads
        """
        self.max_workers = max_workers
        self.task_queue: Queue = Queue()
        self.tasks: Dict[str, Task] = {}
        self.task_handlers: Dict[str, Callable] = {}
        self.executor = ThreadPoolExecutor(max_workers=max_workers)
        self.running = False
        self.workers: List[threading.Thread] = []
        self._lock = threading.Lock()
        
        logger.info(f"Initialized internal queue service with {max_workers} workers")
    
    def register_task_handler(self, task_type: str, handler: Callable) -> None:
        """
        Register a task handler function
        
        Args:
            task_type: Type of task to handle
            handler: Handler function that processes the task
        """
        self.task_handlers[task_type] = handler
        logger.info(f"Registered task handler for type: {task_type}")
    
    def submit_task(self, task_type: str, data: Dict[str, Any]) -> str:
        """
        Submit a task to the queue
        
        Args:
            task_type: Type of task
            data: Task data
            
        Returns:
            Task ID
        """
        task_id = str(uuid.uuid4())
        task = Task(
            task_id=task_id,
            task_type=task_type,
            data=data
        )
        
        with self._lock:
            self.tasks[task_id] = task
            self.task_queue.put(task)
        
        logger.info(f"Submitted task {task_id} of type {task_type}")
        return task_id
    
    def get_task_status(self, task_id: str) -> Optional[Task]:
        """
        Get task status
        
        Args:
            task_id: Task ID
            
        Returns:
            Task object or None if not found
        """
        with self._lock:
            return self.tasks.get(task_id)
    
    def update_task_progress(self, task_id: str, progress: int, status: Optional[TaskStatus] = None) -> None:
        """
        Update task progress
        
        Args:
            task_id: Task ID
            progress: Progress percentage (0-100)
            status: Optional new status
        """
        with self._lock:
            if task_id in self.tasks:
                self.tasks[task_id].progress = progress
                if status:
                    self.tasks[task_id].status = status
                logger.debug(f"Updated task {task_id} progress: {progress}%")
    
    def complete_task(self, task_id: str, result: Dict[str, Any]) -> None:
        """
        Mark task as completed
        
        Args:
            task_id: Task ID
            result: Task result data
        """
        with self._lock:
            if task_id in self.tasks:
                task = self.tasks[task_id]
                task.status = TaskStatus.COMPLETED
                task.completed_at = datetime.now()
                task.result = result
                task.progress = 100
                logger.info(f"Task {task_id} completed successfully")
    
    def fail_task(self, task_id: str, error: str) -> None:
        """
        Mark task as failed
        
        Args:
            task_id: Task ID
            error: Error message
        """
        with self._lock:
            if task_id in self.tasks:
                task = self.tasks[task_id]
                task.status = TaskStatus.FAILED
                task.completed_at = datetime.now()
                task.error = error
                logger.error(f"Task {task_id} failed: {error}")
    
    def _worker_loop(self) -> None:
        """Worker loop that processes tasks from the queue"""
        worker_id = threading.current_thread().name
        logger.info(f"Worker {worker_id} started")
        
        while self.running:
            try:
                # Get task from queue with timeout
                task = self.task_queue.get(timeout=1.0)
                
                # Update task status
                with self._lock:
                    task.status = TaskStatus.PROCESSING
                    task.started_at = datetime.now()
                
                logger.info(f"Worker {worker_id} processing task {task.task_id}")
                
                # Get handler for task type
                handler = self.task_handlers.get(task.task_type)
                if not handler:
                    error_msg = f"No handler registered for task type: {task.task_type}"
                    logger.error(error_msg)
                    self.fail_task(task.task_id, error_msg)
                    continue
                
                try:
                    # Execute task handler
                    result = handler(task)
                    self.complete_task(task.task_id, result)
                    
                except Exception as e:
                    error_msg = f"Task execution failed: {str(e)}"
                    logger.error(error_msg, exc_info=True)
                    self.fail_task(task.task_id, error_msg)
                
                finally:
                    self.task_queue.task_done()
                    
            except Empty:
                # No tasks available, continue loop
                continue
            except Exception as e:
                logger.error(f"Worker {worker_id} encountered error: {e}", exc_info=True)
        
        logger.info(f"Worker {worker_id} stopped")
    
    def start(self) -> None:
        """Start the queue service"""
        if self.running:
            logger.warning("Queue service is already running")
            return
        
        self.running = True
        
        # Start worker threads
        for i in range(self.max_workers):
            worker = threading.Thread(
                target=self._worker_loop,
                name=f"QueueWorker-{i+1}",
                daemon=True
            )
            worker.start()
            self.workers.append(worker)
        
        logger.info(f"Started queue service with {len(self.workers)} workers")
    
    def stop(self) -> None:
        """Stop the queue service"""
        if not self.running:
            logger.warning("Queue service is not running")
            return
        
        logger.info("Stopping queue service...")
        self.running = False
        
        # Wait for workers to finish
        for worker in self.workers:
            worker.join(timeout=5.0)
        
        # Shutdown executor
        self.executor.shutdown(wait=True)
        
        logger.info("Queue service stopped")
    
    def get_queue_stats(self) -> Dict[str, Any]:
        """
        Get queue statistics
        
        Returns:
            Queue statistics
        """
        with self._lock:
            total_tasks = len(self.tasks)
            status_counts = {}
            for task in self.tasks.values():
                status = task.status.value
                status_counts[status] = status_counts.get(status, 0) + 1
        
        return {
            "total_tasks": total_tasks,
            "queue_size": self.task_queue.qsize(),
            "max_workers": self.max_workers,
            "running": self.running,
            "status_counts": status_counts
        }
    
    def cleanup_old_tasks(self, max_age_hours: int = 24) -> int:
        """
        Clean up old completed/failed tasks
        
        Args:
            max_age_hours: Maximum age in hours for tasks to keep
            
        Returns:
            Number of tasks cleaned up
        """
        cutoff_time = datetime.now().timestamp() - (max_age_hours * 3600)
        cleaned_count = 0
        
        with self._lock:
            tasks_to_remove = []
            for task_id, task in self.tasks.items():
                if (task.status in [TaskStatus.COMPLETED, TaskStatus.FAILED] and
                    task.created_at.timestamp() < cutoff_time):
                    tasks_to_remove.append(task_id)
            
            for task_id in tasks_to_remove:
                del self.tasks[task_id]
                cleaned_count += 1
        
        if cleaned_count > 0:
            logger.info(f"Cleaned up {cleaned_count} old tasks")
        
        return cleaned_count


# Global queue service instance
queue_service = None

def get_queue_service():
    """Get the global queue service instance"""
    return queue_service

def set_queue_service(service):
    """Set the global queue service instance"""
    global queue_service
    queue_service = service
