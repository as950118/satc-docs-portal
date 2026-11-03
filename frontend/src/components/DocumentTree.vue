<template>
  <div class="document-tree">
    <!-- 헤더 영역 -->
    <div class="tree-header-section">

      
      <!-- 버튼 영역 (관리자만) -->
      <div class="action-section" v-if="isAdmin">
      <el-button 
        type="primary" 
          @click="showUploadDialogFunc"
          class="upload-button"
        >
          <el-icon><Upload /></el-icon>
          파일 업로드
        </el-button>
        
        <el-button 
          @click="showAddFolderDialogFunc"
          class="folder-button"
        >
          <el-icon><FolderAdd /></el-icon>
          새 폴더
      </el-button>
    </div>
    
      <!-- 통계 영역 -->
      <div class="stats-section" v-if="documentStats">
        <!-- 첫 번째 줄: 전체 폴더와 전체 문서 -->
        <div class="stats-row">
          <div class="stat-box">
            <div class="stat-icon-wrapper">
              <el-icon class="stat-folder-icon"><Folder /></el-icon>
              <span class="stat-num">{{ documentStats.totalFolders }}</span>
            </div>
            <span class="stat-text">전체 폴더</span>
          </div>
          <div class="stat-separator">|</div>
          <div class="stat-box">
            <div class="stat-icon-wrapper">
              <el-icon class="stat-file-icon"><Document /></el-icon>
              <span class="stat-num">{{ documentStats.totalDocuments }}</span>
            </div>
            <span class="stat-text">전체 문서</span>
          </div>
        </div>
        
        <!-- 두 번째 줄: 상태별 문서 (관리자만) -->
        <div class="stats-row" v-if="isAdmin">
          <div class="stat-box status-uploading" style="background: transparent !important; background-color: transparent !important; background-image: none !important; box-shadow: none !important; border: none !important;">
            <div class="stat-icon-wrapper" style="background: transparent !important; background-color: transparent !important;">
              <el-icon class="stat-status-icon" style="background: transparent !important; background-color: transparent !important;"><Loading /></el-icon>
              <span class="stat-num" style="background: transparent !important; background-color: transparent !important;">{{ documentStats.uploadingDocuments }}</span>
            </div>
            <span class="stat-text" style="background: transparent !important; background-color: transparent !important;">업로드 중</span>
          </div>
          <div class="stat-separator">|</div>
          <div class="stat-box status-indexing" style="background: transparent !important; background-color: transparent !important; background-image: none !important; box-shadow: none !important; border: none !important;">
            <div class="stat-icon-wrapper" style="background: transparent !important; background-color: transparent !important;">
              <el-icon class="stat-status-icon" style="background: transparent !important; background-color: transparent !important;"><View /></el-icon>
              <span class="stat-num" style="background: transparent !important; background-color: transparent !important;">{{ documentStats.indexingDocuments }}</span>
            </div>
            <span class="stat-text" style="background: transparent !important; background-color: transparent !important;">인덱싱 중</span>
          </div>
          <div class="stat-separator">|</div>
          <div class="stat-box status-completed">
            <div class="stat-icon-wrapper">
              <el-icon class="stat-status-icon"><Check /></el-icon>
              <span class="stat-num">{{ documentStats.completedDocuments }}</span>
            </div>
            <span class="stat-text">처리 완료</span>
          </div>
        </div>

        <div class="stats-row spec-stats" v-if="specStatusCounts.length" data-test="spec-status-summary">
          <span class="spec-stats-label">PRD·기술문서</span>
          <el-tag v-for="c in specStatusCounts" :key="c.status" size="small" :type="SPEC_STATUS[c.status].tag" :class="statusClass(c.status)">
            {{ SPEC_STATUS[c.status].label }} {{ c.count }}
          </el-tag>
        </div>
      </div>
    </div>

    <!-- 트리 영역 -->
    <div class="tree-content-section">
    <el-tree
      v-if="specsLoaded"
      :data="specTree"
      :props="specTreeProps"
      node-key="id"
      :default-expanded-keys="specExpandedKeys"
      :auto-expand-parent="false"
      :current-node-key="currentSpecId"
      highlight-current
      :expand-on-click-node="true"
      @node-click="openSpec"
      @node-expand="(data) => setSpecFolderExpanded(data.id, true)"
      @node-collapse="(data) => setSpecFolderExpanded(data.id, false)"
      class="document-tree-list spec-tree"
      data-test="spec-tree"
    >
      <template #default="{ data }">
        <div class="tree-node" :data-test="data.spec ? `spec-node-${data.spec.id}` : data.id">
          <div class="node-icon">
            <el-icon v-if="data.isFolder" class="folder-icon spec-folder-icon"><Folder /></el-icon>
            <el-icon v-else class="file-icon"><Document /></el-icon>
          </div>
          <div class="node-content">
            <span class="node-title">{{ data.title }}</span>
            <div v-if="data.spec" class="file-tags">
              <el-tag size="small" :type="SPEC_STATUS[data.spec.status]?.tag" :class="statusClass(data.spec.status)">{{ statusLabel(data.spec.status) }}</el-tag>
              <el-tag v-if="prdChangedLabel(data.spec)" size="small" type="danger" effect="dark">{{ prdChangedLabel(data.spec) }}</el-tag>
            </div>
            <span v-else class="folder-count">
              <span v-if="data.childrenCount > 0">{{ data.childrenCount }}개 문서</span>
              <span v-else class="no-documents">{{ data.emptyHint || '하위 문서 없음' }}</span>
            </span>
          </div>
          <el-button v-if="data.canCreate" type="primary" size="small" class="new-spec-btn" data-test="new-prd"
            @click.stop="$router.push('/specs/new')">
            <el-icon><Plus /></el-icon>새 PRD
          </el-button>
        </div>
      </template>
    </el-tree>
    <el-tree
      :data="treeData"
      :props="treeProps"
      node-key="id"
      :expand-on-click-node="true"
        :lazy="true"
        :load="loadNode"
      @node-click="handleNodeClick"
        v-loading="loading"
        class="document-tree-list"
        ref="treeRef"
        empty-text="문서가 없습니다."
    >
      <template #default="{ node, data }">
        <div class="tree-node">
          <div class="node-icon">
            <el-icon v-if="data.isFolder" class="folder-icon">
            <Folder />
          </el-icon>
            <el-icon v-else class="file-icon">
            <Document />
          </el-icon>
          </div>
          <div class="node-content">
            <span class="node-title">{{ data.title }}</span>
            
            <!-- 파일의 태그들 (확장자 + 상태) -->
            <div v-if="!data.isFolder" class="file-tags">
              <span v-if="data.extension" class="file-extension">
                .{{ data.extension.toLowerCase() }}
              </span>
              <!-- 상태 태그 (관리자만) -->
              <span v-if="data.status && isAdmin" class="status-tag" :class="`status-${data.status.toLowerCase()}`">
                <el-icon v-if="data.status === 'UPLOADING'" class="status-icon"><Loading /></el-icon>
                <el-icon v-else-if="data.status === 'INDEXING'" class="status-icon"><View /></el-icon>
                <el-icon v-else-if="data.status === 'COMPLETE'" class="status-icon"><Check /></el-icon>
                <span class="status-text">
                  {{ data.status === 'UPLOADING' ? '업로드 중' : 
                     data.status === 'INDEXING' ? '인덱싱 중' : 
                     data.status === 'COMPLETE' ? '완료' : 
                     data.status === 'FAILED' ? '인덱싱 실패' : data.status }}
                </span>
              </span>
            </div>
            
            <!-- 폴더의 정보 -->
            <span v-if="data.isFolder" class="folder-count">
              <span v-if="data.childrenCount > 0">{{ data.childrenCount }}개 문서</span>
              <span v-else class="no-documents">하위 문서 없음</span>
            </span>
          </div>
          <!-- 액션 버튼들 (관리자만) -->
          <div class="node-actions" @click.stop v-if="isAdmin">
            <el-button 
              v-if="data.isFolder"
              type="text" 
              size="small"
              @click="addSubDocument(data)"
              class="action-btn upload-btn"
            >
              <el-icon><Upload /></el-icon>
            </el-button>
            <el-button 
              v-if="data.isFolder"
              type="text" 
              size="small"
              @click="addSubFolder(data)"
              class="action-btn folder-add-btn"
            >
              <el-icon><FolderAdd /></el-icon>
            </el-button>
            <el-button 
              type="text" 
              size="small"
              @click="moveDocument(data)"
              class="action-btn move-btn"
            >
              <el-icon><Rank /></el-icon>
            </el-button>
            <el-button 
              type="text" 
              size="small"
              @click="deleteDocument(data)"
              class="action-btn delete-btn"
            >
              <el-icon><Delete /></el-icon>
            </el-button>
          </div>
        </div>
      </template>
    </el-tree>
    </div>

    <!-- 문서 추가 다이얼로그 -->
    <!-- 파일 업로드 대화상자 -->
    <el-dialog
      v-model="showUploadDialog"
      title="파일 업로드"
      width="500px"
      @close="handleUploadDialogClose"
    >
      <el-form :model="uploadForm" label-width="80px">
        <el-form-item label="파일 선택">
          <el-upload
            ref="uploadRef"
            :file-list="fileList"
            :auto-upload="false"
            :on-change="handleFileChange"
            :on-remove="handleFileRemove"
            action="#"
            multiple
            drag
          >
            <el-icon class="el-icon--upload"><Upload /></el-icon>
            <div class="el-upload__text">
              파일을 여기로 드래그하거나 <em>클릭하여 업로드</em>
            </div>
            <template #tip>
              <div class="el-upload__tip">
                여러 파일을 동시에 업로드할 수 있습니다
              </div>
            </template>
          </el-upload>
        </el-form-item>
        <el-form-item label="상위폴더">
          <el-select 
            v-model="uploadForm.parentId" 
            placeholder="상위 폴더 선택 (선택사항)"
            filterable
          >
            <el-option label="최상위" :value="null" />
            <el-option
              v-for="folder in folderList"
              :key="folder.id"
              :label="folder.title"
              :value="folder.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="handleUploadDialogClose">취소</el-button>
          <el-button type="primary" @click="uploadFiles" :disabled="validFileCount === 0">
            업로드 ({{ validFileCount }}개 파일)
          </el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 새 폴더 추가 대화상자 -->
    <el-dialog
      v-model="showAddFolderDialog"
      title="새 폴더 추가"
      width="400px"
    >
      <el-form :model="newFolder" label-width="80px">
        <el-form-item label="제목">
          <el-input v-model="newFolder.title" placeholder="폴더 이름을 입력하세요" />
        </el-form-item>
        <el-form-item label="상위폴더">
          <el-select 
            v-model="newFolder.parentId" 
            placeholder="상위 폴더 선택 (선택사항)"
            filterable
          >
            <el-option label="최상위" :value="null" />
            <el-option
              v-for="folder in folderList"
              :key="folder.id"
              :label="folder.title"
              :value="folder.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="showAddFolderDialog = false">취소</el-button>
          <el-button type="success" @click="addFolder">추가</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 문서/폴더 이동 대화상자 -->
    <el-dialog
      v-model="showMoveDialog"
      title="문서/폴더 이동"
      width="400px"
    >
      <div v-if="moveTarget" class="move-info">
        <p><strong>이동할 항목:</strong> {{ moveTarget.title }}</p>
        <p><strong>유형:</strong> {{ moveTarget.isFolder ? '폴더' : '문서' }}</p>
      </div>
      
      <el-form :model="moveForm" label-width="100px">
        <el-form-item label="이동할 위치">
          <el-select v-model="moveForm.targetParentId" placeholder="대상 폴더 선택 (선택하지 않으면 루트)">
            <el-option
              label="루트 (최상위)"
              :value="null"
            />
            <el-option
              v-for="folder in availableFolders"
              :key="folder.id"
              :label="folder.title"
              :value="folder.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="showMoveDialog = false; allAvailableFolders = []">취소</el-button>
          <el-button type="primary" @click="confirmMove">이동</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import { documentApi } from '@/api'
import { authUtils } from '@/api/auth'
import { SPEC_STATUS, prdChangedLabel, specPath, statusClass, statusLabel } from '@/api/specs'
import { PRD_FOLDER, TECH_FOLDER, folderKeyOf, useSpecTree } from '@/composables/useSpecTree'
import { Check, Delete, Document, Folder, FolderAdd, Loading, Plus, Rank, Upload, View } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { DOCUMENTS_CHANGED } from '@/composables/liveEventsCore'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

export default {
  name: 'DocumentTree',
  components: {
    Upload,
    FolderAdd,
    Folder,
    Document,
    Plus,
    Delete,
    Rank,
    Loading,
    View,
    Check
  },
  emits: ['document-select', 'document-add', 'document-delete', 'tree-data-updated'],
  setup(props, { emit }) {
    const treeData = ref([])
    const documentStats = ref(null)
    const loading = ref(false)

    const route = useRoute()
    const router = useRouter()
    const { specs, loaded: specsLoaded, tree: specTree, statusCounts: specStatusCounts } = useSpecTree()
    const specTreeProps = { children: 'children', label: 'title' }
    const specExpandedKeys = ref([PRD_FOLDER, TECH_FOLDER])
    const currentSpecId = computed(() => route.params.id || null)

    const setSpecFolderExpanded = (key, expanded) => {
      const rest = specExpandedKeys.value.filter((k) => k !== key)
      specExpandedKeys.value = expanded ? [...rest, key] : rest
    }

    // 딥링크로 들어와도 현재 스펙이 보이도록 상위 가상 폴더를 펼친다.
    watch([currentSpecId, specs], () => {
      const current = specs.value.find((s) => s.id === currentSpecId.value)
      if (current) {
        specExpandedKeys.value = [...new Set([...specExpandedKeys.value, ...folderKeyOf(current)])]
      }
    })

    const openSpec = (data) => {
      if (data.spec) {
        router.push(specPath(data.spec))
      }
    }
    
    // 권한 확인
    const isAdmin = computed(() => authUtils.isAdmin())
    const showUploadDialog = ref(false)
    const showAddFolderDialog = ref(false)
    const showMoveDialog = ref(false)
    const uploadForm = ref({
      parentId: null
    })
    const fileList = ref([])
    const uploadRef = ref(null)
    const treeRef = ref(null)
    const newFolder = ref({
      title: '',
      parentId: null
    })
    const moveTarget = ref(null)
    const moveForm = ref({
      targetParentId: null
    })
    const allAvailableFolders = ref([])

    const treeProps = {
      children: 'children',
      label: 'title',
      isLeaf: 'isLeaf'
    }

    // 폴더 목록 - 트리 데이터에서 폴더만 추출
    const folderList = computed(() => {
      const folders = []
      
      const collectFolders = (nodes, parentPath = '') => {
        if (!Array.isArray(nodes)) return
        
        nodes.forEach(node => {
          if (node.isFolder) {
            const folderPath = parentPath ? `${parentPath} > ${node.title}` : node.title
            folders.push({
              id: node.id,
              title: folderPath,
              originalTitle: node.title
            })
            
            // 자식 폴더들도 수집 (이미 로드된 경우에만)
            if (node.children && Array.isArray(node.children)) {
              collectFolders(node.children, folderPath)
            }
          }
        })
      }
      
      collectFolders(treeData.value)
      console.log('생성된 폴더 목록:', folders)
      return folders
    })

    // 유효한 파일 개수 (raw 객체가 있는 파일만)
    const validFileCount = computed(() => {
      return fileList.value.filter(item => item.raw).length
    })



    // 이동 가능한 폴더 목록 (자기 자신은 제외)
    const availableFolders = computed(() => {
      if (!moveTarget.value) return []
      
      // API에서 직접 가져온 폴더 목록 사용
      const sourceFolders = allAvailableFolders.value
      const targetId = moveTarget.value.id
      
      console.log('=== availableFolders computed 실행 ===')
      console.log('이동 대상 ID:', targetId)
      console.log('전체 폴더 목록:', sourceFolders.map(f => `${f.title} (${f.id})`))
      
      // 자기 자신만 제외 (하위 폴더 제외 로직은 백엔드에서 처리 예정)
      const filtered = sourceFolders.filter(folder => folder.id !== targetId)
      console.log('필터링 후 이동 가능한 폴더:', filtered.map(f => `${f.title} (${f.id})`))
      
      return filtered
    })

    // 문서 통계 로드
    const loadDocumentStatistics = async () => {
      try {
        console.log('문서 통계 로드 시작...')
        const response = await documentApi.getDocumentStatistics()
        console.log('통계 API 응답:', response)
        
        if (response.data && response.data.data) {
          documentStats.value = response.data.data
          console.log('통계 데이터 설정 완료:', documentStats.value)
        } else {
          console.warn('통계 데이터 구조가 예상과 다름:', response.data)
          // 기본값 설정
          documentStats.value = {
            totalFolders: 0,
            totalDocuments: 0,
            uploadingDocuments: 0,
            indexingDocuments: 0,
            completedDocuments: 0
          }
        }
      } catch (error) {
        console.error('문서 통계 로드 실패:', error)
        // 에러 시 기본값 설정 (API가 아직 구현되지 않은 경우)
        documentStats.value = {
          totalFolders: 0,
          totalDocuments: 0,
          uploadingDocuments: 0,
          indexingDocuments: 0,
          completedDocuments: 0
        }
        console.log('기본 통계 데이터로 설정:', documentStats.value)
      }
    }

    // 초기 데이터 로드
    onMounted(async () => {
      // 기본 통계 데이터 설정 (API 호출 전)
      documentStats.value = {
        totalFolders: 0,
        totalDocuments: 0,
        uploadingDocuments: 0,
        indexingDocuments: 0,
        completedDocuments: 0
      }
      
      window.addEventListener(DOCUMENTS_CHANGED, reloadFromLive)
      await loadInitialData()
    })

    // 실시간 이벤트(다른 사용자의 변경, 재연결)로 트리와 통계를 조용히 다시 읽는다.
    const reloadFromLive = () => loadInitialData(true)
    onBeforeUnmount(() => window.removeEventListener(DOCUMENTS_CHANGED, reloadFromLive))

    const loadInitialData = async (silent = false) => {
      if (!silent) loading.value = true
      try {
        // 루트 레벨 데이터만 로드
        const response = await fetchDocumentTree()
        // 루트 레벨 노드들을 처리하여 isLeaf 속성 추가 및 filePath에 확장자 추가
        treeData.value = response.data.tree.map(node => {
          console.log('원본 노드 데이터:', node)
          
          let processedFilePath = node.filePath
          // 파일인 경우 filePath에 확장자가 없으면 추가
          if (!node.isFolder && node.filePath && node.extension) {
            if (!node.filePath.endsWith('.' + node.extension)) {
              processedFilePath = `${node.filePath}.${node.extension}`
              console.log('확장자 추가:', node.filePath, '->', processedFilePath)
            }
          }
          
          return {
            ...node,
            isLeaf: !node.isFolder || (node.isFolder && (node.childrenCount === 0)), // 파일이거나 하위 항목이 없는 폴더는 leaf 노드
            children: node.isFolder ? [] : undefined, // 폴더면 빈 children 배열
            filePath: processedFilePath
          }
        })
        
        // 문서 통계 로드 (실패해도 계속 진행)
        try {
          await loadDocumentStatistics()
        } catch (error) {
          console.warn('통계 로드 실패했지만 계속 진행:', error)
        }
      } catch (error) {
        console.error('문서 트리 로드 실패:', error)
        ElMessage.error('문서 트리를 불러오는데 실패했습니다.')
        // 기본 빈 데이터로 설정
        treeData.value = []
        documentStats.value = {
          totalFolders: 0,
          totalDocuments: 0,
          uploadingDocuments: 0,
          indexingDocuments: 0,
          completedDocuments: 0
        }
      } finally {
        loading.value = false
      }
    }

    // treeData가 변경될 때마다 Home 컴포넌트에 알림
    watch(treeData, (newTreeData) => {
      emit('tree-data-updated', newTreeData)
    }, { deep: true })

    const fetchDocumentTree = async () => {
      try {
        // 실제 API 호출
        const response = await documentApi.getDocumentTree()
        return response.data
      } catch (error) {
        console.error('API 호출 실패:', error)
        // API 호출 실패 시 샘플 데이터 반환
        return {
          responseTime: "2025-09-01T17:23:37.717354",
          errorMessage: null,
          data: {
            tree: [
              {
                id: "0201563b-f9a1-40f6-bb90-7ef7eb6da199",
                parentId: null,
                title: "tree-test",
                extension: "txt",
                filePath: "documents/0201563b-f9a1-40f6-bb90-7ef7eb6da199.txt",
                isFolder: false,
                children: [],
                childrenCount: 0
              },
              {
                id: "folder-1",
                parentId: null,
                title: "개발 가이드",
                extension: null,
                filePath: null,
                isFolder: true,
                children: [],
                childrenCount: 3
              },
              {
                id: "folder-2",
                parentId: null,
                title: "회사 정책",
                extension: null,
                filePath: null,
                isFolder: true,
                children: [],
                childrenCount: 1
              }
            ],
            totalCount: 6,
            folderCount: 2,
            fileCount: 4
          },
          status: 200
        }
      }
    }

    // 노드 지연 로딩 함수
    const loadNode = async (node, resolve) => {
      try {
        console.log('loadNode 호출:', node.level, node.data)
        
        if (node.level === 0) {
          // 루트 레벨 - 이미 loadInitialData에서 처리됨
          return resolve([])
        }

        // 폴더의 자식 요소들 로드
        if (node.data && node.data.isFolder) {
          console.log('폴더 자식 요소 로드 시작:', node.data.id)
          const children = await fetchFolderChildren(node.data.id)
          console.log('받은 자식 데이터:', children)
          
          // children이 배열인지 확인
          if (!Array.isArray(children)) {
            console.error('자식 데이터가 배열이 아닙니다:', children)
            resolve([])
            return
          }
          
          // children에 isLeaf 속성 추가 및 filePath에 확장자 추가
          const processedChildren = children.map(child => {
            console.log('자식 노드 원본 데이터:', child)
            
            let processedFilePath = child.filePath
            // 파일인 경우 filePath에 확장자가 없으면 추가
            if (!child.isFolder && child.filePath && child.extension) {
              if (!child.filePath.endsWith('.' + child.extension)) {
                processedFilePath = `${child.filePath}.${child.extension}`
                console.log('자식 노드 확장자 추가:', child.filePath, '->', processedFilePath)
              }
            }
            
            return {
              ...child,
              isLeaf: !child.isFolder || (child.isFolder && (child.childrenCount === 0)), // 파일이거나 하위 항목이 없는 폴더는 leaf 노드
              children: child.isFolder ? [] : undefined,
              filePath: processedFilePath
            }
          })
          
          console.log('처리된 자식 데이터:', processedChildren)
          resolve(processedChildren)
        } else {
          resolve([])
        }
      } catch (error) {
        console.error('자식 노드 로드 실패:', error)
        ElMessage.error('폴더 내용을 불러오는데 실패했습니다.')
        resolve([])
      }
    }

    // 특정 폴더의 자식 요소들을 가져오는 함수
    const fetchFolderChildren = async (folderId) => {
      try {
        // 실제 API 호출
        const response = await documentApi.getFolderChildren(folderId)
        console.log('API 응답:', response)
        console.log('API 응답 데이터:', response.data)
        
        // 응답 구조에 따라 데이터 추출
        let childrenData = []
        
        if (response.data && response.data.data) {
          // 응답 구조가 { data: { tree: [...] } } 형태인 경우
          if (response.data.data.tree) {
            childrenData = response.data.data.tree
          }
          // 응답 구조가 { data: [...] } 형태인 경우
          else if (Array.isArray(response.data.data)) {
            childrenData = response.data.data
          }
          // 응답 구조가 { data: { children: [...] } } 형태인 경우
          else if (response.data.data.children) {
            childrenData = response.data.data.children
          }
        }
        // 응답 구조가 { tree: [...] } 형태인 경우
        else if (response.data && response.data.tree) {
          childrenData = response.data.tree
        }
        // 응답 구조가 직접 배열인 경우
        else if (Array.isArray(response.data)) {
          childrenData = response.data
        }
        
        console.log('추출된 자식 데이터:', childrenData)
        return childrenData
      } catch (error) {
        console.error('폴더 자식 요소 API 호출 실패:', error)
        console.error('에러 상세:', error.response?.data, error.message)
        // API 호출 실패 시 샘플 데이터 반환
        return getSampleChildren(folderId)
      }
    }

    // 샘플 자식 데이터 생성
    const getSampleChildren = (folderId) => {
      const sampleChildren = {
        'folder-1': [
          {
            id: "doc-1",
            parentId: "folder-1", 
            title: "Vue.js 가이드",
            extension: "md",
            filePath: "documents/doc-1.md",
            isFolder: false,
            children: [],
            childrenCount: 0
          },
          {
            id: "doc-2",
            parentId: "folder-1",
            title: "API 문서", 
            extension: "md",
            filePath: "documents/doc-2.md",
            isFolder: false,
            children: [],
            childrenCount: 0
          },
          {
            id: "subfolder-1",
            parentId: "folder-1",
            title: "하위 폴더",
            extension: null,
            filePath: null,
            isFolder: true,
            children: [],
            childrenCount: 1
          }
        ],
        'folder-2': [
          {
            id: "doc-3",
            parentId: "folder-2",
            title: "보안 정책",
            extension: "pdf",
            filePath: "documents/doc-3.pdf",
            isFolder: false,
            children: [],
            childrenCount: 0
          }
        ],
        'subfolder-1': [
          {
            id: "doc-4",
            parentId: "subfolder-1",
            title: "코딩 컨벤션",
            extension: "md",
            filePath: "documents/doc-4.md",
            isFolder: false,
            children: [],
            childrenCount: 0
          }
        ]
      }
      return sampleChildren[folderId] || []
    }

    const loadSampleData = () => {
      treeData.value = [
        {
          id: "0201563b-f9a1-40f6-bb90-7ef7eb6da199",
          parentId: null,
          title: "tree-test",
          extension: "txt",
          filePath: "documents/0201563b-f9a1-40f6-bb90-7ef7eb6da199.txt",
          isFolder: false,
          isLeaf: true,
          children: undefined,
          childrenCount: 0
        }
      ]
      treeStats.value = {
        totalCount: 1,
        folderCount: 0,
        fileCount: 1
      }
    }

    const handleNodeClick = (data) => {
      if (!data.isFolder) {
        // 업로드 중인 문서에 대한 안내 메시지
        if (data.status === 'UPLOADING') {
          ElMessage({
            message: '파일이 업로드 중입니다. 잠시 후에 다시 시도해주세요.',
            type: 'info',
            duration: 3000
          })
        }
        
        emit('document-select', data)
      }
    }

    // 파일 업로드 대화상자 표시
    const showUploadDialogFunc = () => {
      uploadForm.value = {
        parentId: null
      }
      fileList.value = []
      showUploadDialog.value = true
    }

    // 파일 업로드 대화상자 닫기 처리
    const handleUploadDialogClose = () => {
      console.log('업로드 대화상자 닫기 - 초기화 시작')
      
      // 다이얼로그 닫기
      showUploadDialog.value = false
      
      // 폼 데이터 초기화
      uploadForm.value = {
        parentId: null
      }
      
      // 파일 리스트 초기화
      fileList.value = []
      
      // el-upload 컴포넌트 초기화
      if (uploadRef.value) {
        uploadRef.value.clearFiles()
        console.log('el-upload 컴포넌트 파일 리스트 초기화 완료')
      }
      
      console.log('업로드 대화상자 초기화 완료')
    }

    // 새 폴더 대화상자 표시
    const showAddFolderDialogFunc = () => {
      newFolder.value = {
        title: '',
        parentId: null
      }
      showAddFolderDialog.value = true
    }

    // 파일 변경 처리
    const handleFileChange = (file, fileListParam) => {
      console.log('=== 파일 변경 이벤트 ===')
      console.log('추가된 파일:', {
        name: file.name,
        size: file.size,
        type: file.type,
        status: file.status,
        uid: file.uid,
        hasRaw: !!file.raw
      })
      console.log('전체 파일 리스트:', fileListParam.map(item => ({
        name: item.name,
        status: item.status,
        uid: item.uid,
        hasRaw: !!item.raw
      })))
      
      // fileList를 업데이트
      fileList.value = fileListParam
      console.log('업데이트된 fileList.value 길이:', fileList.value.length)
    }

    // 파일 제거 처리
    const handleFileRemove = (file, fileListParam) => {
      console.log('파일 제거:', file.name)
      console.log('현재 파일 리스트 길이:', fileListParam.length)
      // fileList를 업데이트
      fileList.value = fileListParam
      console.log('업데이트된 fileList.value 길이:', fileList.value.length)
    }

    // 파일 업로드 실행
    const uploadFiles = async () => {
      console.log('=== 업로드 실행 시작 ===')
      console.log('업로드 버튼 클릭됨!')
      console.log('현재 fileList.value 길이:', fileList.value.length)
      console.log('유효한 파일 개수:', validFileCount.value)
      
      if (fileList.value.length === 0) {
        ElMessage.warning('업로드할 파일을 선택해주세요.')
        return
      }
      
      if (validFileCount.value === 0) {
        ElMessage.warning('유효한 파일이 없습니다. 파일을 다시 선택해주세요.')
        return
      }

      // 업로드 시작 안내
      const loadingMessage = ElMessage({
        message: `${fileList.value.length}개 파일을 업로드하고 있습니다...`,
        type: 'info',
        duration: 0, // 자동으로 사라지지 않음
        showClose: false
      })

      try {
        console.log('원본 fileList.value:', fileList.value)
        console.log('fileList.value 구조 확인:', fileList.value.map(item => ({
          name: item.name,
          raw: item.raw,
          status: item.status,
          uid: item.uid,
          hasRaw: !!item.raw
        })))
        
        const files = fileList.value.map(item => item.raw).filter(Boolean)
        console.log('추출된 파일들:', files)
        console.log('업로드할 파일들:', files.map(f => ({ name: f.name, size: f.size, type: f.type })))
        console.log('상위 폴더 ID:', uploadForm.value.parentId)
        
        if (files.length === 0) {
          throw new Error('업로드할 파일이 없습니다. 파일을 다시 선택해주세요.')
        }
        
        if (files.length !== fileList.value.length) {
          console.warn('일부 파일의 raw 객체가 누락되었습니다.')
          console.warn('원본 파일 수:', fileList.value.length, '실제 파일 수:', files.length)
        }
        
        let response
        if (files.length === 1) {
          // 단일 파일 업로드
          console.log('단일 파일 업로드 API 호출 시작')
          response = await documentApi.uploadFile(files[0], uploadForm.value.parentId)
          console.log('단일 파일 업로드 API 응답:', response)
        } else {
          // 다중 파일 업로드
          console.log('다중 파일 업로드 API 호출 시작')
          response = await documentApi.uploadMultipleFiles(files, uploadForm.value.parentId)
          console.log('다중 파일 업로드 API 응답:', response)
        }

        // 백엔드 응답 처리
        if (files.length === 1) {
          // 단일 파일의 경우 - DocumentCreateResponse 구조로 변경됨
          const doc = response.data.data
          console.log('단일 파일 업로드 - 백엔드에서 받은 문서 데이터:', doc)
          
          let newDoc
          
          // boolean 응답인 경우 (기존 방식 호환)
          if (typeof doc === 'boolean' && doc === true) {
            const file = files[0]
            const titleParts = file.name.split('.')
            const extension = titleParts.length > 1 ? titleParts.pop() : ''
            const titleWithoutExtension = titleParts.join('.')
            
            newDoc = {
              id: `temp-${Date.now()}`, // 임시 ID
              parentId: uploadForm.value.parentId,
              title: titleWithoutExtension,
              extension: extension,
              filePath: `documents/temp-${Date.now()}${extension ? '.' + extension : ''}`,
              isFolder: false,
              children: [],
              childrenCount: 0,
              isLeaf: true
            }
          } else {
            // DocumentCreateResponse 구조인 경우 (새로운 방식)
            newDoc = {
              id: doc.documentId,
              parentId: uploadForm.value.parentId,
              title: doc.title, // 백엔드에서 받은 제목 그대로 사용
              extension: doc.extension, // 백엔드에서 제공하는 extension 필드 사용
              filePath: `documents/${doc.documentId}${doc.extension ? '.' + doc.extension : ''}`,
              isFolder: doc.isFolder,
              children: [],
              childrenCount: 0,
              isLeaf: !doc.isFolder
            }
          }

          // UI에 추가
          await addToTree(newDoc)
          
          // 통계 업데이트
          await loadDocumentStatistics()

          emit('document-add', newDoc)
          
          // 로딩 메시지 닫기
          loadingMessage.close()
          
          // 업로드 완료 안내 및 자동 새로고침 예약
          ElMessage({
            message: '파일이 업로드되었습니다. 잠시 후 자동으로 새로고침됩니다.',
            type: 'success',
            duration: 5000,
            showClose: true
          })
          
          // 5초 후 자동 새로고침
          setTimeout(async () => {
            console.log('업로드 후 자동 새로고침 실행...')
            await loadInitialData()
            await loadDocumentStatistics()
            ElMessage({
              message: '문서 목록이 새로고침되었습니다.',
              type: 'info',
              duration: 2000
            })
          }, 5000)
        } else {
          // 다중 파일의 경우 DocumentCreateResponse[] 응답
          const uploadedDocuments = response.data.data
          console.log('다중 파일 업로드 응답 데이터 구조:', response.data)
          console.log('업로드된 문서 배열:', uploadedDocuments)
          
          // 응답 데이터 검증
          if (!Array.isArray(uploadedDocuments)) {
            console.error('다중 파일 업로드 응답이 배열이 아닙니다:', uploadedDocuments)
            throw new Error('다중 파일 업로드 응답 형식이 올바르지 않습니다.')
          }
          
          if (uploadedDocuments.length === 0) {
            console.warn('업로드된 문서가 없습니다.')
            throw new Error('업로드된 문서가 없습니다.')
          }
          
          for (const doc of uploadedDocuments) {
            console.log('백엔드에서 받은 문서 데이터:', doc)
            
            // 필수 필드 검증
            if (!doc.documentId) {
              console.error('문서 ID가 없습니다:', doc)
              continue
      }

      const newDoc = {
              id: doc.documentId,
              parentId: uploadForm.value.parentId,
              title: doc.title, // 백엔드에서 받은 제목 그대로 사용
              extension: doc.extension, // 백엔드에서 제공하는 extension 필드 사용
              filePath: `documents/${doc.documentId}${doc.extension ? '.' + doc.extension : ''}`,
              isFolder: doc.isFolder,
              children: [],
              childrenCount: 0,
              isLeaf: !doc.isFolder
            }

            // UI에 추가
            await addToTree(newDoc)
            
            // 통계 업데이트
            await loadDocumentStatistics()

            emit('document-add', newDoc)
          }

          // 로딩 메시지 닫기
          loadingMessage.close()

          // 다중 파일 업로드 완료 안내 및 자동 새로고침 예약
          ElMessage({
            message: `${uploadedDocuments.length}개 파일이 업로드되었습니다. 잠시 후 자동으로 새로고침됩니다.`,
            type: 'success',
            duration: 5000,
            showClose: true
          })
          
          // 5초 후 자동 새로고침
          setTimeout(async () => {
            console.log('다중 파일 업로드 후 자동 새로고침 실행...')
            await loadInitialData()
            await loadDocumentStatistics()
            ElMessage({
              message: '문서 목록이 새로고침되었습니다.',
              type: 'info',
              duration: 2000
            })
          }, 5000)
        }
        
        // 폼 초기화 및 대화상자 닫기
        handleUploadDialogClose()

      } catch (error) {
        // 로딩 메시지 닫기
        loadingMessage.close()
        
        console.error('파일 업로드 실패:', error)
        ElMessage.error('파일 업로드에 실패했습니다.')
      }
    }

    // 새 폴더 추가 (백엔드 동기 응답)
    const addFolder = async () => {
      if (!newFolder.value.title.trim()) {
        ElMessage.warning('폴더 이름을 입력해주세요.')
        return
      }

      try {
        // 백엔드 API 호출 (새로운 API 명세에 맞게)
        const response = await documentApi.createFolder(
          newFolder.value.title,
          newFolder.value.parentId
        )

        // 백엔드에서 받은 동기 응답 (DocumentCreateResponse)
        const createdFolder = response.data.data
        
        const newFolderDoc = {
          id: createdFolder.documentId,
          parentId: newFolder.value.parentId,
          title: createdFolder.title,
          extension: null,
          filePath: null,
          isFolder: createdFolder.isFolder,
          children: [],
          childrenCount: 0,
          isLeaf: true // 새로 생성된 폴더는 하위 항목이 없으므로 leaf 노드
        }

        // UI에 추가
        await addToTree(newFolderDoc)
        
        // 통계 업데이트
        await loadDocumentStatistics()

        // 폴더 추가는 별도 이벤트로 처리하거나 emit 하지 않음
        ElMessage.success(`폴더 "${newFolderDoc.title}"가 추가되었습니다.`)
        
        // 폼 초기화 및 대화상자 닫기
        newFolder.value = { title: '', parentId: null }
        showAddFolderDialog.value = false

      } catch (error) {
        console.error('폴더 추가 실패:', error)
        ElMessage.error('폴더 추가에 실패했습니다.')
      }
    }

    // 트리에 새 항목 추가하는 공통 함수
    const addToTree = async (newItem) => {
      console.log('addToTree 호출:', newItem)
      if (newItem.parentId) {
        // 상위 폴더에 추가
        const addToParent = (nodes) => {
          for (let node of nodes) {
            if (node.id === newItem.parentId) {
              if (!node.children) {
                node.children = []
              }
              node.children.push(newItem)
              // childrenCount는 문서(파일)의 개수만 카운트
              if (!newItem.isFolder) {
                node.childrenCount = (node.childrenCount || 0) + 1
                console.log(`부모 폴더 "${node.title}"의 문서 개수 증가:`, node.childrenCount)
              } else {
                console.log(`부모 폴더 "${node.title}"에 하위 폴더 추가됨 (문서 개수 변화 없음)`)
              }
              // 하위 항목이 추가되었으므로 부모 폴더는 더 이상 leaf 노드가 아님
              if (node.isLeaf) {
                node.isLeaf = false
                console.log(`부모 폴더 "${node.title}"가 더 이상 leaf 노드가 아님`)
              }
              return node
            }
            if (node.children && node.children.length > 0) {
              const result = addToParent(node.children)
              if (result) return result
            }
          }
          return null
        }
        
        const parentNode = addToParent(treeData.value)
        if (!parentNode) {
          console.error('부모 폴더를 찾을 수 없습니다:', newItem.parentId)
        } else {
          // Vue의 반응성을 강제로 트리거
          await nextTick()
        }
      } else {
        // 최상위에 추가
        treeData.value.push(newItem)
        console.log('최상위에 추가됨:', newItem.title)
      }
    }

    const addSubDocument = (parentNode) => {
      uploadForm.value.parentId = parentNode.id
      showUploadDialog.value = true
    }

    // 하위 폴더 추가
    const addSubFolder = (parentNode) => {
      newFolder.value = {
        title: '',
        parentId: parentNode.id
      }
      showAddFolderDialog.value = true
    }

    // 백엔드에서 모든 폴더 목록을 직접 가져오기 (API 명세 v1.0)
    const loadAllFoldersFromAPI = async () => {
      try {
        console.log('폴더 목록 API 호출 시작...')
        const response = await documentApi.getAllFolders()
        console.log('폴더 목록 API 응답:', response)
        
        // 새로운 API 응답 구조: { data: FolderListItem[] }
        if (response.data && Array.isArray(response.data.data)) {
          const folders = response.data.data
          console.log(`총 ${folders.length}개 폴더 로드됨:`, folders.map(f => f.title))
          return folders
        } else if (response.data && Array.isArray(response.data)) {
          // 혹시 data가 직접 배열인 경우도 처리
          console.log('직접 배열 형태로 폴더 데이터 수신:', response.data.length, '개')
          return response.data
        } else {
          console.warn('예상과 다른 폴더 API 응답 구조:', response.data)
          return []
        }
      } catch (error) {
        console.error('폴더 목록 로드 실패:', error)
        ElMessage.warning('폴더 목록을 불러오는데 실패했습니다.')
        return []
      }
    }

    // 문서/폴더 이동 대화상자 표시
    const moveDocument = async (data) => {
      console.log('=== 이동 다이얼로그 열기 ===')
      console.log('이동 대상:', data.title, '(id:', data.id, ', isFolder:', data.isFolder, ')')
      
      moveTarget.value = data
      moveForm.value = {
        targetParentId: null
      }
      
      // 백엔드에서 직접 모든 폴더 목록 가져오기
      const allFolders = await loadAllFoldersFromAPI()
      allAvailableFolders.value = allFolders
      
      console.log('이동 가능한 폴더 목록 설정 완료:', allFolders.length, '개')
      console.log('필터링 전 폴더들:', allFolders.map(f => `${f.title} (${f.id})`))
      
      showMoveDialog.value = true
    }

    // 이동 확인 및 실행
    const confirmMove = async () => {
      if (!moveTarget.value) return

      try {
        console.log('문서 이동 시작:', {
          documentIds: [moveTarget.value.id],
          targetParentId: moveForm.value.targetParentId
        })

        const response = await documentApi.moveDocuments(
          [moveTarget.value.id],
          moveForm.value.targetParentId
        )

        if (response.data?.data === true) {
          // 성공 시 트리 새로고침
          await loadInitialData()
          
          const targetName = moveForm.value.targetParentId 
            ? folderList.value.find(f => f.id === moveForm.value.targetParentId)?.title || '선택된 폴더'
            : '루트'
          
          ElMessage.success(`"${moveTarget.value.title}"가 ${targetName}로 이동되었습니다.`)
          
          // 대화상자 닫기
          showMoveDialog.value = false
          moveTarget.value = null
          allAvailableFolders.value = [] // API에서 가져온 폴더 목록 초기화
        } else {
          throw new Error('이동 실패')
        }

      } catch (error) {
        console.error('문서 이동 실패:', error)
        ElMessage.error(`문서 이동에 실패했습니다: ${error.response?.data?.errorMessage || error.message}`)
      }
    }

    const deleteDocument = async (data) => {
      try {
        // 폴더와 파일에 따른 다른 확인 메시지
        const isFolder = data.isFolder
        const itemType = isFolder ? '폴더' : '문서'
        const warningMessage = isFolder 
          ? `<div class="delete-message-content">
               <div class="delete-title"><strong>${data.title}</strong> 폴더를 삭제하시겠습니까?</div>
               <div class="delete-warning">
                 <div class="warning-text">
                   <strong>주의:</strong> 폴더 내의 모든 하위 폴더와 문서가 함께 삭제됩니다.
                 </div>
               </div>
             </div>`
          : `<div class="delete-message-content">
               <div class="delete-title"><strong>${data.title}</strong> 문서를 삭제하시겠습니까?</div>
             </div>`

        await ElMessageBox.confirm(
          warningMessage,
          `${itemType} 삭제 확인`,
          {
            confirmButtonText: '삭제',
            cancelButtonText: '취소',
            type: 'error',
            dangerouslyUseHTMLString: true,
            customClass: 'premium-delete-dialog',
            confirmButtonClass: 'premium-delete-btn el-button--danger',
            cancelButtonClass: 'premium-cancel-btn'
          }
        )

        // API 호출로 문서/폴더 삭제
        console.log(`${itemType} 삭제 API 호출:`, data.id)
        const response = await documentApi.deleteDocument(data.id)
        
        // 응답 확인
        if (response.data && response.data.data === true) {
          // 트리 전체 새로고침으로 최신 상태 반영
          console.log(`${itemType} 삭제 성공, 트리 새로고침 중...`)
          await loadInitialData()
          
          emit('document-delete', data)
          
          // 성공 메시지도 구분
          const successMessage = isFolder 
            ? `폴더 "${data.title}" 및 하위 문서들이 삭제되었습니다.`
            : `문서 "${data.title}"가 삭제되었습니다.`
          
          ElMessage.success(successMessage)
        } else {
          ElMessage.error(`${itemType} 삭제에 실패했습니다.`)
        }
      } catch (error) {
        // 사용자가 취소한 경우 (cancel)는 에러 메시지 표시하지 않음
        if (error === 'cancel' || error.message === 'cancel') {
          console.log('사용자가 삭제를 취소했습니다.')
          return
        }
        
        const itemType = data.isFolder ? '폴더' : '문서'
        console.error(`${itemType} 삭제 실패:`, error)
        ElMessage.error(`${itemType} 삭제 중 오류가 발생했습니다.`)
      }
    }

    return {
      treeData,
      documentStats,
      loading,
      treeProps,
      showUploadDialog,
      showAddFolderDialog,
      showMoveDialog,
      uploadForm,
      fileList,
      uploadRef,
      treeRef,
      newFolder,
      moveTarget,
      moveForm,
      folderList,
      validFileCount,
      availableFolders,
      isAdmin,
      loadNode,
      handleNodeClick,
      showUploadDialogFunc,
      handleUploadDialogClose,
      showAddFolderDialogFunc,
      handleFileChange,
      handleFileRemove,
      uploadFiles,
      addFolder,
      addSubDocument,
      addSubFolder,
      moveDocument,
      confirmMove,
      deleteDocument,
      loadDocumentStatistics,
      SPEC_STATUS,
      statusLabel,
      statusClass,
      prdChangedLabel,
      specsLoaded,
      specTree,
      specTreeProps,
      specStatusCounts,
      specExpandedKeys,
      currentSpecId,
      setSpecFolderExpanded,
      openSpec
    }
  }
}
</script>

<style scoped>
.document-tree {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #ffffff;
  overflow: hidden;
}

/* 헤더 섹션 */
.tree-header-section {
  flex-shrink: 0;
  background: var(--mt-surface);
  border-bottom: 1px solid var(--mt-hairline);
  padding: 24px;
}

/* 제목 영역 */
.header-top {
  margin-bottom: 20px;
  text-align: center;
}

.main-title {
  font-size: 22px;
  font-weight: 600;
  color: var(--mt-ink);
  margin: 0 0 6px 0;
  text-shadow: none;
}

.main-subtitle {
  font-size: 13px;
  color: var(--mt-steel);
  margin: 0;
}

/* 버튼 영역 */
.action-section {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
}

.upload-button {
  flex: 1;
  height: 48px;
}

.folder-button {
  flex: 1;
  height: 48px;
}

.upload-button :deep(.el-icon) {
  margin-right: 12px;
}

.folder-button :deep(.el-icon) {
  margin-right: 12px;
}

/* 통계 영역 */
.stats-section {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 16px;
  background: var(--mt-surface-soft);
  border-radius: var(--mt-r-lg);
  border: 1px solid var(--mt-hairline);
}

.stats-row {
  display: flex;
  align-items: center;
  justify-content: center;
}

.stat-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  flex: 1;
  background: none !important;
  background-color: transparent !important;
  background-image: none !important;
  box-shadow: none !important;
  border: none !important;
}

.stat-icon-wrapper {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.stat-folder-icon {
  color: var(--mt-warn);
  font-size: 18px;
}

.stat-file-icon {
  color: var(--mt-steel);
  font-size: 18px;
}

.stat-num {
  font-size: 20px;
  font-weight: 600;
  color: var(--mt-ink);
  line-height: 1;
}

.stat-text {
  font-size: 13px;
  color: var(--mt-steel);
  font-weight: 500;
}

.stat-separator {
  color: var(--mt-hairline);
  margin: 0 12px;
  font-weight: 300;
}

/* 상태별 통계 스타일 */
.status-uploading,
.status-uploading *,
.status-uploading .stat-box,
.status-uploading .stat-icon-wrapper,
.status-uploading .stat-status-icon,
.status-uploading .stat-num,
.status-uploading .stat-text {
  background-color: transparent !important;
  background: none !important;
  background-image: none !important;
  box-shadow: none !important;
  border: none !important;
}

.status-uploading .stat-status-icon {
  color: var(--mt-warn) !important;
  animation: spin 2s linear infinite;
}

.status-indexing,
.status-indexing *,
.status-indexing .stat-box,
.status-indexing .stat-icon-wrapper,
.status-indexing .stat-status-icon,
.status-indexing .stat-num,
.status-indexing .stat-text {
  background-color: transparent !important;
  background: none !important;
  background-image: none !important;
  box-shadow: none !important;
  border: none !important;
}

.status-indexing .stat-status-icon {
  color: var(--mt-ink) !important;
}

.status-completed .stat-status-icon {
  color: var(--mt-green-deep);
}

.status-uploading .stat-num {
  color: var(--mt-warn) !important;
}

.status-indexing .stat-num {
  color: var(--mt-ink) !important;
}

.status-completed .stat-num {
  color: var(--mt-green-deep);
}

/* FAILED 상태 - 인덱싱 실패 */
.status-failed {
  background: transparent !important;
  color: var(--mt-error, #c0392b) !important;
  border-color: var(--mt-error, #c0392b) !important;
}

.status-failed .status-text {
  color: var(--mt-error, #c0392b) !important;
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

/* 강력한 배경색 제거 - 모든 가능한 선택자 */
.stat-box.status-uploading,
.stat-box.status-indexing,
div.stat-box.status-uploading,
div.stat-box.status-indexing,
.stats-row .stat-box.status-uploading,
.stats-row .stat-box.status-indexing,
.stats-section .stat-box.status-uploading,
.stats-section .stat-box.status-indexing {
  background: transparent !important;
  background-color: transparent !important;
  background-image: none !important;
  background-attachment: initial !important;
  background-blend-mode: initial !important;
  background-clip: initial !important;
  background-origin: initial !important;
  background-position: initial !important;
  background-repeat: initial !important;
  background-size: initial !important;
  box-shadow: none !important;
  border: none !important;
  border-radius: 0 !important;
}

/* Element Plus 컴포넌트 내부 스타일 오버라이드 */
:deep(.status-uploading),
:deep(.status-uploading *),
:deep(.status-uploading .el-icon),
:deep(.status-uploading .stat-box),
:deep(.status-uploading .stat-icon-wrapper),
:deep(.stat-box.status-uploading),
:deep(div.stat-box.status-uploading) {
  background-color: transparent !important;
  background: none !important;
  background-image: none !important;
  box-shadow: none !important;
  border: none !important;
}

:deep(.status-indexing),
:deep(.status-indexing *),
:deep(.status-indexing .el-icon),
:deep(.status-indexing .stat-box),
:deep(.status-indexing .stat-icon-wrapper),
:deep(.stat-box.status-indexing),
:deep(div.stat-box.status-indexing) {
  background-color: transparent !important;
  background: none !important;
  background-image: none !important;
  box-shadow: none !important;
  border: none !important;
}

/* 전역 오버라이드 */
* .status-uploading,
* .status-indexing {
  background: transparent !important;
  background-color: transparent !important;
}

/* 트리 콘텐츠 섹션 */
.tree-content-section {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
}

.tree-content-section::-webkit-scrollbar {
  width: 6px;
}

.tree-content-section::-webkit-scrollbar-track {
  background: var(--mt-surface);
}

.tree-content-section::-webkit-scrollbar-thumb {
  background: var(--mt-hairline);
  border-radius: 3px;
}

.tree-content-section::-webkit-scrollbar-thumb:hover {
  background: var(--mt-stone);
}

/* 프리미엄 삭제 다이얼로그 스타일 */
:deep(.premium-delete-dialog) {
  border-radius: 20px;
  box-shadow: 
    0 25px 50px rgba(0, 0, 0, 0.15),
    0 0 0 1px rgba(255, 255, 255, 0.05);
  border: none;
  backdrop-filter: blur(20px);
  overflow: hidden;
}

:deep(.premium-delete-dialog .el-message-box__header) {
  background: #fbeeee;
  border-radius: 20px 20px 0 0;
  padding: 32px 32px 20px;
  border-bottom: 1px solid rgba(212,86,86, 0.08);
  position: relative;
}

:deep(.premium-delete-dialog .el-message-box__header::before) {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 4px;
  background: var(--mt-error);
}

:deep(.premium-delete-dialog .el-message-box__title) {
  color: var(--mt-error);
  font-weight: 700;
  font-size: 20px;
  text-align: center;
  text-shadow: none;
}

:deep(.premium-delete-dialog .el-message-box__content) {
  padding: 32px;
  background: #ffffff;
}

:deep(.premium-delete-dialog .el-message-box__message) {
  margin: 0;
  padding: 0;
}

/* 메시지 내용 스타일 */
:deep(.delete-message-content) {
  text-align: center;
}

:deep(.delete-title) {
  font-size: 16px;
  font-weight: 600;
  color: var(--mt-ink);
  margin-bottom: 20px;
  line-height: 1.5;
}

:deep(.delete-warning) {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  background: #fbeeee;
  padding: 20px;
  border-radius: 16px;
  border: 1px solid rgba(212,86,86, 0.1);
  margin-top: 16px;
}

:deep(.warning-icon) {
  font-size: 24px;
  flex-shrink: 0;
  margin-top: 2px;
}

:deep(.warning-text) {
  font-size: 14px;
  color: var(--mt-error);
  line-height: 1.6;
  text-align: left;
}

:deep(.warning-text strong) {
  color: var(--mt-error);
  font-weight: 600;
}

:deep(.premium-delete-dialog .el-message-box__btns) {
  padding: 24px 32px 32px;
  background: #ffffff;
  border-radius: 0 0 20px 20px;
  gap: 16px;
  display: flex;
  justify-content: center;
}

/* 프리미엄 삭제 버튼 - 빨간색 최고 우선순위 강제 적용 */
:deep(.el-message-box__btns .premium-delete-btn),
:deep(.el-message-box__btns .premium-delete-btn.el-button),
:deep(.el-message-box__btns .premium-delete-btn.el-button--primary),
:deep(.el-message-box__btns .premium-delete-btn.el-button.el-button--primary),
:deep(.premium-delete-dialog .el-message-box__btns .premium-delete-btn),
:deep(.premium-delete-dialog .el-message-box__btns .el-button.premium-delete-btn) {
  background-color: var(--mt-error) !important;
  background-image: var(--mt-error) !important;
  background: var(--mt-error) !important;
  border-color: var(--mt-error) !important;
  border: 2px solid var(--mt-error) !important;
  border-radius: 12px !important;
  padding: 14px 28px !important;
  font-weight: 600 !important;
  font-size: 14px !important;
  color: #ffffff !important;
  box-shadow: 
    0 4px 12px rgba(212,86,86, 0.4),
    inset 0 1px 0 rgba(255, 255, 255, 0.2) !important;
  transition: all 0.3s ease !important;
  min-width: 100px !important;
}

:deep(.el-message-box__btns .premium-delete-btn:hover),
:deep(.el-message-box__btns .premium-delete-btn.el-button:hover),
:deep(.el-message-box__btns .premium-delete-btn.el-button--primary:hover),
:deep(.el-message-box__btns .premium-delete-btn.el-button.el-button--primary:hover),
:deep(.premium-delete-dialog .el-message-box__btns .premium-delete-btn:hover),
:deep(.premium-delete-dialog .el-message-box__btns .el-button.premium-delete-btn:hover) {
  background-color: var(--mt-error) !important;
  background-image: var(--mt-error) !important;
  background: var(--mt-error) !important;
  border-color: var(--mt-error) !important;
  transform: translateY(-2px) !important;
  box-shadow: 
    0 8px 20px rgba(212,86,86, 0.5),
    inset 0 1px 0 rgba(255, 255, 255, 0.2) !important;
  color: #ffffff !important;
}

:deep(.el-message-box__btns .premium-delete-btn:active),
:deep(.el-message-box__btns .premium-delete-btn.el-button:active),
:deep(.el-message-box__btns .premium-delete-btn.el-button--primary:active),
:deep(.el-message-box__btns .premium-delete-btn.el-button.el-button--primary:active),
:deep(.premium-delete-dialog .el-message-box__btns .premium-delete-btn:active),
:deep(.premium-delete-dialog .el-message-box__btns .el-button.premium-delete-btn:active) {
  transform: translateY(0) !important;
  box-shadow: 
    0 2px 8px rgba(212,86,86, 0.4),
    inset 0 1px 0 rgba(255, 255, 255, 0.2) !important;
  background-color: var(--mt-error) !important;
  background-image: var(--mt-error) !important;
  background: var(--mt-error) !important;
  border-color: var(--mt-error) !important;
  color: #ffffff !important;
}

/* Element Plus 기본 스타일 완전 오버라이드 */
:deep(.el-button--primary.premium-delete-btn),
:deep(.el-button--danger.premium-delete-btn) {
  --el-button-bg-color: var(--mt-error) !important;
  --el-button-border-color: var(--mt-error) !important;
  --el-button-hover-bg-color: var(--mt-error) !important;
  --el-button-hover-border-color: var(--mt-error) !important;
  --el-button-active-bg-color: var(--mt-error) !important;
  --el-button-active-border-color: var(--mt-error) !important;
  --el-color-danger: var(--mt-error) !important;
  --el-color-danger-light-3: var(--mt-error) !important;
  --el-color-danger-dark-2: var(--mt-error) !important;
}

/* Danger 버튼 추가 스타일 */
:deep(.el-message-box__btns .premium-delete-btn.el-button--danger),
:deep(.premium-delete-dialog .el-message-box__btns .premium-delete-btn.el-button--danger) {
  background-color: var(--mt-error) !important;
  background-image: var(--mt-error) !important;
  background: var(--mt-error) !important;
  border-color: var(--mt-error) !important;
  border: 2px solid var(--mt-error) !important;
  color: #ffffff !important;
}

/* 프리미엄 취소 버튼 */
:deep(.premium-cancel-btn),
:deep(.premium-cancel-btn.el-button) {
  background: #ffffff !important;
  color: var(--mt-steel) !important;
  border: 1px solid var(--mt-hairline) !important;
  border-radius: 12px !important;
  padding: 14px 28px !important;
  font-weight: 500 !important;
  font-size: 14px !important;
  box-shadow: 
    0 2px 4px rgba(0, 0, 0, 0.05),
    inset 0 1px 0 rgba(255, 255, 255, 0.9) !important;
  transition: all 0.3s ease !important;
  min-width: 100px !important;
}

:deep(.premium-cancel-btn:hover),
:deep(.premium-cancel-btn.el-button:hover) {
  background: var(--mt-surface) !important;
  border-color: var(--mt-hairline) !important;
  color: var(--mt-slate) !important;
  transform: translateY(-1px) !important;
  box-shadow: 
    0 4px 8px rgba(0, 0, 0, 0.1),
    inset 0 1px 0 rgba(255, 255, 255, 0.9) !important;
}

:deep(.premium-cancel-btn:active),
:deep(.premium-cancel-btn.el-button:active) {
  transform: translateY(0) !important;
  box-shadow: 
    0 1px 2px rgba(0, 0, 0, 0.05),
    inset 0 1px 0 rgba(255, 255, 255, 0.9) !important;
  background: var(--mt-surface) !important;
  color: var(--mt-slate) !important;
}

.tree-node {
  display: flex;
  align-items: center;
  flex: 1;
  padding: 8px 12px;
  border-radius: var(--mt-r-md);
  transition: all 0.2s ease;
  position: relative;
  margin: 0 -4px;
  min-width: 0; /* flex 아이템이 줄어들 수 있도록 */
}

.tree-node:hover {
  background: var(--mt-surface);
}

.node-icon {
  margin-right: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: var(--mt-r-sm);
  transition: all 0.2s ease;
  flex-shrink: 0;
}

.folder-icon {
  color: var(--mt-warn);
  font-size: 20px;
  background: transparent;
  border-radius: 8px;
}

.file-icon {
  color: var(--mt-steel);
  font-size: 18px;
  background: transparent;
  border-radius: 8px;
}

.node-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  gap: 4px;
  overflow: hidden; /* 내용이 넘치지 않도록 */
}

.node-title {
  font-weight: 500;
  color: var(--mt-ink);
  font-size: 15px;
  line-height: 1.4;
  letter-spacing: -0.01em;
  transition: all 0.2s ease;
}

/* 호버 시에만 줄임표 적용 */
.tree-node:hover .node-title {
  text-overflow: ellipsis;
  overflow: hidden;
  white-space: nowrap;
}

/* 호버 시 node-content가 액션 버튼 공간을 확보하도록 */
.tree-node:hover .node-content {
  max-width: calc(100% - 140px); /* 액션 버튼들을 위한 공간 확보 */
}

.file-tags {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.file-extension {
  color: var(--mt-steel);
  font-size: 12px;
  font-weight: 600;
  background: var(--mt-surface);
  padding: 2px 6px;
  border-radius: 6px;
  border: 1px solid var(--mt-hairline);
  font-family: var(--mt-font-mono);
  letter-spacing: 0.5px;
  flex-shrink: 0;
}

.status-tag {
  display: inline-flex;
  align-items: center;
  font-size: 12px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: var(--mt-r-full);
  gap: 4px;
  border-width: 1px;
  border-style: solid;
  flex-shrink: 0;
}

.status-icon {
  font-size: 12px;
  flex-shrink: 0;
}

.status-text {
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}

/* UPLOADING 상태 - 주황색 */
.status-uploading {
  background: #fbf3e3 !important;
  color: var(--mt-warn) !important;
  border-color: #f0cccc !important;
}

.status-uploading .status-icon {
  color: var(--mt-warn) !important;
  animation: spin 1s linear infinite;
}

.status-uploading .status-text {
  color: var(--mt-warn) !important;
}

/* INDEXING 상태 - 파란색 */
.status-indexing {
  background: var(--mt-green-tint) !important;
  color: var(--mt-ink) !important;
  border-color: var(--mt-hairline) !important;
}

.status-indexing .status-icon {
  color: var(--mt-ink) !important;
}

.status-indexing .status-text {
  color: var(--mt-ink) !important;
}

/* COMPLETE 상태 - 초록색 */
.status-complete {
  background: var(--mt-green-tint) !important;
  color: var(--mt-ink) !important;
  border-color: var(--mt-hairline) !important;
}

.status-complete .status-icon {
  color: var(--mt-ink) !important;
}

.status-complete .status-text {
  color: var(--mt-ink) !important;
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

/* 추가 강제 적용 */
.tree-node .status-indexing,
.tree-node .status-indexing .status-text,
.tree-node .status-indexing .status-icon {
  color: var(--mt-ink) !important;
}

.tree-node .status-uploading,
.tree-node .status-uploading .status-text,
.tree-node .status-uploading .status-icon {
  color: var(--mt-warn) !important;
}

.tree-node .status-complete,
.tree-node .status-complete .status-text,
.tree-node .status-complete .status-icon {
  color: var(--mt-ink) !important;
}

.folder-count {
  color: var(--mt-stone);
  font-size: 12px;
  font-weight: 400;
}

.no-documents {
  color: var(--mt-stone);
  font-style: italic;
  opacity: 0.7;
}

.node-actions {
  display: none;
  gap: 6px;
  margin-left: 12px;
  align-items: center;
  flex-shrink: 0; /* 버튼들이 줄어들지 않도록 */
  min-width: fit-content; /* 필요한 최소 너비 확보 */
}

.tree-node:hover .node-actions {
  display: flex;
}

.action-btn {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
  border: none;
  background: transparent;
  cursor: pointer;
}

.upload-btn {
  color: var(--mt-ink);
}

.upload-btn:hover {
  background: rgba(0,180,138, 0.1);
  color: var(--mt-ink);
  transform: scale(1.1);
}

.folder-add-btn {
  color: var(--mt-steel);
}

.folder-add-btn:hover {
  background: rgba(10,10,10, 0.08);
  color: var(--mt-steel);
  transform: scale(1.1);
}

.move-btn {
  color: var(--mt-green-deep);
}

.move-btn:hover {
  background: rgba(0,180,138, 0.1);
  color: var(--mt-green-deep);
  transform: scale(1.1);
}

.delete-btn {
  color: var(--mt-error);
}

.delete-btn:hover {
  background: rgba(212,86,86, 0.1);
  color: var(--mt-error);
  transform: scale(1.1);
}

.document-tree-list {
  height: 100%;
  background: transparent;
  padding: 16px;
}

:deep(.el-tree) {
  background: transparent;
}

:deep(.el-tree-node__content) {
  height: auto;
  min-height: 48px;
  border-radius: 12px;
  margin-bottom: 2px;
  padding: 0;
  background: transparent;
  border: 1px solid transparent;
  transition: all 0.2s ease;
}

:deep(.el-tree-node__content:hover) {
  background: var(--mt-surface);
  border-color: transparent;
}

:deep(.el-tree-node__expand-icon) {
  color: var(--mt-steel);
  font-size: 16px;
  margin-right: 12px;
  transition: all 0.2s ease;
}

:deep(.el-tree-node__expand-icon.expanded) {
  transform: rotate(90deg);
  color: var(--mt-ink);
}

:deep(.el-tree-node__label) {
  color: transparent;
  font-size: 0;
}

:deep(.el-button--primary),
:deep(.el-button--success) {
  border-radius: var(--mt-r-full);
  box-shadow: none;
}

/* 이동 정보 영역 스타일 */
.move-info {
  background: var(--mt-green-tint);
  border: 1px solid rgba(0,180,138, 0.2);
  border-radius: 12px;
  padding: 16px;
  margin-bottom: 20px;
}

.move-info p {
  margin: 8px 0;
  color: var(--mt-ink);
  font-size: 14px;
}

.move-info strong {
  color: var(--mt-ink);
  font-weight: 600;
}

/* 빈 상태 메시지 스타일 - 간단하게 */
:deep(.el-tree__empty-block) {
  padding: 30px 20px !important;
  background: transparent !important;
  border: none !important;
  margin: 10px !important;
}

:deep(.el-tree__empty-text) {
  color: var(--mt-stone) !important;
  font-size: 14px !important;
  font-weight: 400 !important;
  line-height: 1.4 !important;
}

.spec-stats {
  flex-wrap: wrap;
  gap: 6px;
}

.spec-stats-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--mt-green-text);
  margin-right: 4px;
}

.spec-tree,
.spec-tree + .document-tree-list {
  height: auto;
}

.spec-tree {
  padding-bottom: 8px;
  border-bottom: 1px solid var(--mt-hairline);
}

.spec-folder-icon {
  color: var(--mt-green-deep);
  background: rgba(0,180,138, 0.08);
}

.spec-tree .tree-node:hover .node-content {
  max-width: none;
}

.spec-tree :deep(.el-tree-node.is-current > .el-tree-node__content) {
  background: var(--mt-green-tint);
  border-color: transparent;
  box-shadow: inset 3px 0 0 var(--mt-green);
}

.new-spec-btn {
  flex-shrink: 0;
  margin-left: 12px;
}

.new-spec-btn .el-icon {
  margin-right: 4px;
}
</style>
