"""
FastAPI main application
"""
import logging
from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from app.config import settings
from app.api.routes import router
from app.services.queue_service import InternalQueueService, set_queue_service

# Configure logging
logging.basicConfig(
    level=logging.INFO if not settings.debug else logging.DEBUG,
    format="%(asctime)s - %(name)s - %(levelname)s - %(message)s"
)
logger = logging.getLogger(__name__)

# Initialize global queue service
queue_service = InternalQueueService(max_workers=settings.queue_max_workers)
set_queue_service(queue_service)

# Create FastAPI application
app = FastAPI(
    title="Document Indexing Server",
    description="FastAPI 서버로 MinIO에서 파일을 다운로드하고 청킹하여 Elasticsearch에 인덱싱합니다.",
    version="1.0.0",
    docs_url="/docs",
    redoc_url="/redoc"
)

# Add CORS middleware
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # Configure appropriately for production
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Include API routes
app.include_router(router, prefix="/api/v1", tags=["Document Processing"])


@app.exception_handler(Exception)
async def global_exception_handler(request: Request, exc: Exception):
    """Global exception handler"""
    logger.error(f"Unhandled exception: {exc}", exc_info=True)
    return JSONResponse(
        status_code=500,
        content={
            "error": "내부 서버 오류가 발생했습니다.",
            "detail": str(exc) if settings.debug else "Internal server error"
        }
    )


@app.on_event("startup")
async def startup_event():
    """Application startup event"""
    logger.info("Document Indexing Server starting up...")
    logger.info(f"MinIO endpoint: {settings.minio_endpoint}")
    logger.info(f"Elasticsearch: {settings.elasticsearch_host}:{settings.elasticsearch_port}")
    
    # Register task handlers
    from app.services.document_processor import process_document_handler, health_check_handler
    queue_service.register_task_handler("process_document", process_document_handler)
    queue_service.register_task_handler("health_check", health_check_handler)
    
    # Start internal queue service
    queue_service.start()
    logger.info("Internal queue service started")
    
    logger.info("Server is ready to accept requests")


@app.on_event("shutdown")
async def shutdown_event():
    """Application shutdown event"""
    logger.info("Document Indexing Server shutting down...")
    
    # Stop internal queue service
    queue_service.stop()
    logger.info("Internal queue service stopped")


@app.get("/")
async def root():
    """Root endpoint"""
    return {
        "message": "Document Indexing Server",
        "version": "1.0.0",
        "status": "running",
        "docs": "/docs",
        "api_base": "/api/v1"
    }


@app.get("/ping")
async def ping():
    """Simple ping endpoint for health checks"""
    return {"message": "pong"}


if __name__ == "__main__":
    import uvicorn
    
    uvicorn.run(
        "app.main:app",
        host=settings.server_host,
        port=settings.server_port,
        reload=settings.debug,
        log_level="info" if not settings.debug else "debug"
    )
