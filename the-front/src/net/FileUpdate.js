import { ref } from 'vue';
import axios from 'axios';

/**
 * 文件/图片上传组合式函数
 * @param {Object} options
 * @param {string} options.uploadUrl — 上传接口地址，默认为 '/api/upload/file'
 * @param {Object} options.headers — 附加请求头（如 Authorization）
 * @returns {{
 *   fileInputRef: import('vue').Ref<HTMLInputElement|null>,
 *   selectedFile: import('vue').Ref<File|null>,
 *   uploading: import('vue').Ref<boolean>,
 *   uploadProgress: import('vue').Ref<number>,
 *   uploadStatus: import('vue').Ref<string>,
 *   triggerFileInput: () => void,
 *   handleFileChange: (event: Event) => void,
 *   uploadFile: (extraData?: Record<string, any>) => Promise<any>,
 *   formatBytes: (bytes: number, decimals?: number) => string
 * }}
 */
export function useFileUpload(options = {}) {
  const { uploadUrl = '/api/upload/file', headers = {} } = options;

  // —— 响应式状态 —— 
  const fileInputRef = ref(null);       // 绑定到 <input type="file" />
  const selectedFile   = ref(null);     // 用户选中的文件
  const uploading      = ref(false);    // 上传中状态
  const uploadProgress = ref(0);        // 上传进度（%）
  const uploadStatus   = ref('');       // 上传状态文案

  // —— 触发文件选择 —— 
  const triggerFileInput = () => {
    fileInputRef.value?.click();
  };

  // —— 文件选择回调 —— 
  const handleFileChange = (event) => {
    const files = event.target.files;
    if (files && files.length > 0) {
      selectedFile.value = files[0];
      uploading.value = false;
      uploadProgress.value = 0;
      uploadStatus.value = '';
    } else {
      selectedFile.value = null;
    }
  };

  // —— 格式化字节数 —— 
  const formatBytes = (bytes, decimals = 2) => {
    if (bytes === 0) return '0 Bytes';
    const k = 1024;
    const dm = decimals < 0 ? 0 : decimals;
    const sizes = ['Bytes', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(dm)) + ' ' + sizes[i];
  };

  // —— 执行上传 —— 
  const uploadFile = async (extraData = {}) => {
    if (!selectedFile.value) {
      uploadStatus.value = '请先选择文件';
      return null;
    }

    uploading.value = true;
    uploadProgress.value = 0;
    uploadStatus.value = '开始上传...';

    const formData = new FormData();
    formData.append('file', selectedFile.value);
    // 附加额外数据（如 userId、chatRoomId 等）
    Object.entries(extraData).forEach(([key, val]) => {
      formData.append(key, val);
    });

    try {
      const resp = await axios.post(uploadUrl, formData, {
        headers: {
          'Content-Type': 'multipart/form-data',
          ...headers
        },
        onUploadProgress: (e) => {
          if (e.total > 0) {
            uploadProgress.value = Math.round((e.loaded * 100) / e.total);
          }
        }
      });
      uploading.value = false;
      uploadStatus.value = '上传成功';
      return resp.data;  // 后端返回的文件信息
    } catch (err) {
      uploading.value = false;
      uploadStatus.value = '上传失败';
      console.error('文件上传失败：', err);
      throw err;
    } finally {
      // 上传后清空选择的文件，重置 input
      selectedFile.value = null;
      if (fileInputRef.value) {
        fileInputRef.value.value = '';
      }
    }
  };

  return {
    fileInputRef,
    selectedFile,
    uploading,
    uploadProgress,
    uploadStatus,
    triggerFileInput,
    handleFileChange,
    uploadFile,
    formatBytes
  };
}

/*
在 Vue 组件中调用示例（<script setup>）：

import { useFileUpload } from '@/net/FileUpdate.js';

const {
  fileInputRef,
  selectedFile,
  uploading,
  uploadProgress,
  uploadStatus,
  triggerFileInput,
  handleFileChange,
  uploadFile,
  formatBytes
} = useFileUpload({
  uploadUrl: '/api/upload/image',               // 可选：覆盖默认接口
  headers: { Authorization: `Bearer ${token}` } // 可选：自定义请求头
});

// 模板示例：
<input
  type="file"
  ref="fileInputRef"
  @change="handleFileChange"
  accept="image/*"
  style="display:none"
/>
<button @click="triggerFileInput">选择图片</button>
<div v-if="selectedFile">
  {{ selectedFile.name }} ({{ formatBytes(selectedFile.size) }})
  <button @click="async () => {
    const data = await uploadFile({ chatRoomId: roomId });
    // 上传成功后，通过 WebSocket 发送 data.fileUrl 等给后端/其他用户
  }}">上传</button>
</div>
<div v-if="uploading">上传中：{{ uploadProgress }}%</div>
<div>{{ uploadStatus }}</div>
*/ 