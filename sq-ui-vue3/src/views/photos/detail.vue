<template>
  <div
    class="album-detail"
    v-loading="loading"
    @contextmenu="onPageContextMenu"
    @click="closeCtxMenu"
  >
    <header class="detail-header">
      <div class="header-left">
        <button type="button" class="back-btn" title="返回" @click="goBack">
          <el-icon :size="22"><ArrowLeft /></el-icon>
        </button>
        <div class="header-info">
          <h1 class="album-title" :title="album.albumName">{{ album.albumName || '相册' }}</h1>
          <div
            class="album-desc"
            :class="{ placeholder: !album.albumDesc }"
            @click="startEditDesc"
          >
            <template v-if="editingDesc">
              <el-input
                ref="descInputRef"
                v-model="descDraft"
                size="small"
                maxlength="200"
                placeholder="添加相册描述"
                @keyup.enter="saveDesc"
                @blur="saveDesc"
              />
            </template>
            <template v-else>
              {{ album.albumDesc || '点击添加相册描述' }}
            </template>
          </div>
        </div>
      </div>
      <div class="header-right">
        <el-button round plain @click="openPhotoMap">
          <el-icon class="mr4"><Location /></el-icon>
          照片地图
        </el-button>
        <el-button round type="primary" plain @click="triggerUpload">
          <el-icon class="mr4"><Plus /></el-icon>
          添加照片
        </el-button>
        <input
          ref="fileInputRef"
          type="file"
          accept="image/*,video/*"
          multiple
          class="hidden-input"
          @change="onFilesSelected"
        />
        <input
          ref="folderInputRef"
          type="file"
          accept="image/*,video/*"
          multiple
          webkitdirectory
          directory
          class="hidden-input"
          @change="onFilesSelected"
        />
      </div>
    </header>

    <div class="detail-toolbar">
      <div class="toolbar-left">
        <button
          type="button"
          class="select-btn"
          :class="selectBtnClass"
          :title="selectBtnTitle"
          @click="onSelectHeaderClick"
        >
          <el-icon v-if="isAllSelected" :size="16"><Select /></el-icon>
          <el-icon v-else-if="selectedIds.length" :size="16"><Minus /></el-icon>
          <el-icon v-else :size="18"><CircleCheck /></el-icon>
        </button>
        <span v-if="selectedIds.length" class="selected-count">已选 {{ selectedIds.length }} 项</span>
        <span v-else class="item-count">共 {{ total }} 项</span>
      </div>
      <div class="toolbar-right">
        <el-dropdown trigger="click" @command="handleFilterType">
          <button type="button" class="tool-btn">
            <el-icon><Operation /></el-icon>
            <span>{{ typeFilterLabel }}</span>
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="all">全部</el-dropdown-item>
              <el-dropdown-item command="1">仅图片</el-dropdown-item>
              <el-dropdown-item command="2">仅视频</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <el-dropdown trigger="click" @command="handleScoreFilter">
          <button type="button" class="tool-btn">
            <el-icon><Star /></el-icon>
            <span>{{ scoreFilterLabel }}</span>
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="all">全部评分</el-dropdown-item>
              <el-dropdown-item command="pass">仅合格</el-dropdown-item>
              <el-dropdown-item command="fail">仅不合格</el-dropdown-item>
              <el-dropdown-item command="unscored">未打分</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <button type="button" class="tool-btn" title="为照片打出图质量分，合格才图生图" @click="runPhotoScore">
          <el-icon><Medal /></el-icon>
          <span>质量打分</span>
        </button>
        <button type="button" class="tool-btn" title="对已选或当前预览的合格照片 AI 出图" @click="openDrawDialogFromToolbar">
          <el-icon><Brush /></el-icon>
          <span>AI 出图</span>
        </button>
        <el-dropdown trigger="click" @command="handleOriginFilter">
          <button type="button" class="tool-btn">
            <el-icon><Picture /></el-icon>
            <span>{{ originFilterLabel }}</span>
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="original">仅原片</el-dropdown-item>
              <el-dropdown-item command="aiDraw">仅 AI 创作</el-dropdown-item>
              <el-dropdown-item command="all">全部内容</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <el-dropdown trigger="click" @command="handleShootTimeOrder">
          <button type="button" class="tool-btn">
            <el-icon><Sort /></el-icon>
            <span>{{ shootTimeOrderLabel }}</span>
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="desc">拍摄时间降序</el-dropdown-item>
              <el-dropdown-item command="asc">拍摄时间升序</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <el-dropdown trigger="click" @command="handleSizeMode">
          <button type="button" class="tool-btn">
            <el-icon><Menu /></el-icon>
            <span>{{ sizeModeLabel }}</span>
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="small">小图模式</el-dropdown-item>
              <el-dropdown-item command="medium">中图模式</el-dropdown-item>
              <el-dropdown-item command="large">大图模式</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <button type="button" class="tool-btn icon-only" title="刷新" @click="reload">
          <el-icon><Refresh /></el-icon>
        </button>
      </div>
    </div>

    <div v-if="!photoList.length && !loading" class="empty-box">
      <el-empty :description="emptyHint" />
    </div>

    <div v-else ref="gridWrapRef" class="photo-grid-wrap">
      <div class="photo-grid-phantom" :style="{ height: gridTotalHeight + 'px' }" aria-hidden="true" />
      <div
        class="photo-grid"
        :class="{ 'is-selecting': isSelecting }"
        :style="gridWindowStyle"
      >
        <div
          v-for="item in visiblePhotos"
          :key="item.photoId"
          class="photo-cell"
          :class="{ selected: isSelected(item.photoId) }"
          @click="onItemClick(item, $event)"
          @dblclick.prevent="onItemDblClick(item)"
          @contextmenu.prevent.stop="onItemContextMenu(item, $event)"
        >
          <div class="photo-inner">
            <template v-if="item.fileType === 2">
              <img
                v-if="canShowThumb(item)"
                :src="thumbSrc(item)"
                :alt="item.fileName"
                loading="lazy"
                decoding="async"
                @error="onThumbError(item)"
              />
              <div v-else class="thumb-placeholder" aria-hidden="true" />
              <div class="video-mark">
                <el-icon :size="14"><VideoPlay /></el-icon>
                <span v-if="formatDuration(item.duration)" class="video-duration">{{ formatDuration(item.duration) }}</span>
              </div>
            </template>
            <img
              v-else-if="canShowThumb(item)"
              :src="thumbSrc(item)"
              :alt="item.fileName"
              loading="lazy"
              decoding="async"
              @error="onThumbError(item)"
            />
            <div v-else class="thumb-placeholder" aria-hidden="true" />
            <div v-if="item.aestheticScore != null" class="score-mark" :class="item.scorePass === 1 ? 'pass' : 'fail'">
              {{ item.aestheticScore }}
            </div>
            <div v-if="isAiDraw(item)" class="ai-mark">AI</div>
            <div class="check-mark" aria-hidden="true">
              <el-icon :size="14"><Select /></el-icon>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div v-if="photoList.length" class="detail-footer">
      <div
        v-if="hasMore"
        ref="loadMoreSentinel"
        class="load-more-sentinel"
        role="button"
        tabindex="0"
        @click="loadMore(true)"
        @keydown.enter.prevent="loadMore(true)"
      >
        <span v-if="loadingMore">加载中…</span>
        <span v-else>加载更多</span>
      </div>
      <span v-else>没有更多了</span>
    </div>

    <!-- 选中操作栏 -->
    <teleport to="body">
      <transition name="sel-bar">
        <div v-if="selectedIds.length" class="selection-bar" @click.stop>
          <button type="button" class="sel-btn" title="AI 出图" @click="openDrawDialogFromSelection">
            <el-icon :size="20"><Brush /></el-icon>
          </button>
          <button type="button" class="sel-btn" title="下载" @click="downloadSelected">
            <el-icon :size="20"><Download /></el-icon>
          </button>
          <button type="button" class="sel-btn" title="添加到..." @click="openAddToAlbum">
            <el-icon :size="20"><FolderAdd /></el-icon>
          </button>
          <button type="button" class="sel-btn" title="删除" @click="removeSelected">
            <el-icon :size="20"><Delete /></el-icon>
          </button>
          <button type="button" class="sel-btn" title="取消多选" @click="cancelMultiSelect">
            <el-icon :size="20"><CircleClose /></el-icon>
          </button>
        </div>
      </transition>
    </teleport>

    <!-- 右键菜单：空白 / 图片视频 -->
    <teleport to="body">
      <div
        v-if="ctxMenu.visible"
        class="photos-ctx-menu"
        :class="'type-' + ctxMenu.type"
        :style="{ left: ctxMenu.x + 'px', top: ctxMenu.y + 'px' }"
        @click.stop
        @contextmenu.prevent
      >
        <template v-if="ctxMenu.type === 'blank'">
          <button type="button" class="ctx-item" @click="onCtxUploadFiles">
            <el-icon :size="18"><Picture /></el-icon>
            <span>上传照片/视频</span>
          </button>
          <button type="button" class="ctx-item" @click="onCtxUploadFolder">
            <el-icon :size="18"><FolderOpened /></el-icon>
            <span>上传文件夹</span>
          </button>
          <div class="ctx-divider"></div>
          <button type="button" class="ctx-item" @click="onCtxCreateAlbum">
            <el-icon :size="18"><Files /></el-icon>
            <span>创建相册</span>
          </button>
          <div class="ctx-divider"></div>
          <button type="button" class="ctx-item" @click="onCtxRefresh">
            <el-icon :size="18"><Refresh /></el-icon>
            <span>刷新页面</span>
          </button>
        </template>
        <template v-else>
          <button type="button" class="ctx-item" @click="onItemCtxDownload">下载</button>
          <div class="ctx-divider"></div>
          <button type="button" class="ctx-item" @click="onItemCtxAddTo">添加到...</button>
          <button type="button" class="ctx-item" @click="onItemCtxDetail">查看详细信息</button>
          <button
            v-if="ctxTargetPhoto && ctxTargetPhoto.fileType !== 2 && ctxTargetPhoto.scorePass === 1 && !isAiDraw(ctxTargetPhoto)"
            type="button"
            class="ctx-item"
            @click="onItemCtxDraw"
          >AI 出图</button>
          <button type="button" class="ctx-item" @click="onItemCtxSetCover">设置为相册封面</button>
          <div class="ctx-divider"></div>
          <button type="button" class="ctx-item danger" @click="onItemCtxRemoveFromAlbum">从当前相册移除</button>
          <button type="button" class="ctx-item danger" @click="onItemCtxTrash">放入回收站</button>
        </template>
      </div>
    </teleport>

    <!-- 上传进度 -->
    <teleport to="body">
      <transition name="upload-fade">
        <div v-if="uploading" class="upload-progress-mask" @click.stop @contextmenu.prevent>
          <div class="upload-progress-panel" role="dialog" aria-label="上传进度">
            <div class="upload-progress-title">正在上传</div>
            <div class="upload-progress-summary">
              <span>{{ uploadProgress.done }}/{{ uploadProgress.total }}</span>
              <span>{{ uploadPercent }}%</span>
            </div>
            <el-progress
              :percentage="uploadPercent"
              :stroke-width="10"
              :show-text="false"
              striped
              striped-flow
            />
            <div class="upload-progress-current" :title="uploadCurrentName">
              {{ uploadStatusText }}
            </div>
            <ul v-if="uploadItems.length" class="upload-progress-list">
              <li
                v-for="item in uploadItemsVisible"
                :key="item.id"
                class="upload-progress-item"
                :class="'is-' + item.status"
              >
                <span class="upload-item-name" :title="item.name">{{ item.name }}</span>
                <span class="upload-item-meta">{{ uploadItemLabel(item) }}</span>
              </li>
            </ul>
            <div class="upload-progress-tip">上传完成前请勿关闭或刷新页面</div>
          </div>
        </div>
      </transition>
    </teleport>

    <el-dialog
      v-model="createAlbumOpen"
      title="创建相册"
      width="420px"
      append-to-body
      :z-index="4200"
      @closed="resetCreateAlbumForm"
    >
      <el-form ref="createAlbumFormRef" :model="createAlbumForm" :rules="createAlbumRules" label-width="88px">
        <el-form-item label="相册名称" prop="albumName">
          <el-input v-model="createAlbumForm.albumName" maxlength="50" placeholder="请输入相册名称" />
        </el-form-item>
        <el-form-item label="描述" prop="albumDesc">
          <el-input v-model="createAlbumForm.albumDesc" type="textarea" :rows="3" placeholder="可选" />
        </el-form-item>
        <el-form-item label="公开状态" prop="isPublic">
          <el-radio-group v-model="createAlbumForm.isPublic">
            <el-radio :value="1">公开</el-radio>
            <el-radio :value="0">私有</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createAlbumOpen = false">取消</el-button>
        <el-button type="primary" :loading="creatingAlbumPage" @click="submitCreateAlbum">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="addToOpen"
      title="添加到"
      width="520px"
      append-to-body
      align-center
      class="add-to-dialog"
      :z-index="4100"
      destroy-on-close
      @open="onAddToOpen"
      @closed="resetAddToState"
    >
      <div class="add-to-list" v-loading="albumLoading">
        <div v-if="creatingAlbum" class="add-to-row is-editing">
          <div class="add-to-cover is-placeholder">
            <el-icon :size="22"><Files /></el-icon>
          </div>
          <input
            ref="newAlbumInputRef"
            v-model="newAlbumName"
            class="add-to-name-input"
            maxlength="50"
            @keyup.enter="confirmCreateAlbum"
            @click.stop
          />
          <button type="button" class="add-to-icon-btn ok" title="确认" @click.stop="confirmCreateAlbum">
            <el-icon :size="16"><Select /></el-icon>
          </button>
          <button type="button" class="add-to-icon-btn" title="取消" @click.stop="cancelCreateAlbum">
            <el-icon :size="16"><Close /></el-icon>
          </button>
        </div>

        <div
          v-for="item in albumOptions"
          :key="item.albumId"
          class="add-to-row"
          :class="{ selected: String(targetAlbumId) === String(item.albumId) }"
          @click="selectTargetAlbum(item)"
        >
          <div class="add-to-cover" :class="{ 'is-placeholder': !albumCoverSrc(item) }">
            <img v-if="albumCoverSrc(item)" :src="albumCoverSrc(item)" :alt="item.albumName" />
            <el-icon v-else :size="22"><PictureFilled /></el-icon>
          </div>
          <div class="add-to-name" :title="item.albumName">{{ item.albumName }}</div>
          <div class="add-to-count">{{ item.photoCount ?? 0 }}</div>
        </div>

        <div v-if="!albumLoading && !albumOptions.length && !creatingAlbum" class="add-to-empty">
          暂无其他相册，可先新建
        </div>
      </div>

      <template #footer>
        <div class="add-to-footer">
          <button type="button" class="add-to-create-link" @click="startCreateAlbum">新建相册</button>
          <div class="add-to-footer-actions">
            <el-button @click="addToOpen = false">取消</el-button>
            <el-button
              type="primary"
              :disabled="!canSubmitAddTo"
              :loading="addingTo"
              @click="submitAddToAlbum"
            >
              添加
            </el-button>
          </div>
        </div>
      </template>
    </el-dialog>

    <!-- 图片/视频预览（白底；点击空白不关闭，仅返回按钮 / Esc） -->
    <teleport to="body">
      <div v-if="mediaVisible" class="media-viewer" :class="{ 'detail-open': detailOpen }">
        <!-- 媒体展示区：抽屉打开时收缩，图片与底部工具栏随之适配 -->
        <div class="media-stage">
          <button
            v-if="mediaIndex > 0"
            type="button"
            class="media-nav prev"
            title="上一张"
            @click.stop="shiftMedia(-1)"
          >
            <el-icon :size="22"><ArrowLeft /></el-icon>
          </button>
          <button
            v-if="mediaIndex < photoList.length - 1"
            type="button"
            class="media-nav next"
            title="下一张"
            @click.stop="shiftMedia(1)"
          >
            <el-icon :size="22"><ArrowRight /></el-icon>
          </button>

          <div
            v-if="currentMedia && currentMedia.fileType !== 2"
            class="media-canvas"
            @wheel.prevent="onImageWheel"
            @dblclick.prevent="onImageDblClick"
            @dragstart.prevent
          >
            <!-- 变换挂在轻量 wrapper 上，避免直接缩放大图位图导致卡顿 -->
            <div ref="mediaImageLayerRef" class="media-image-layer">
              <img
                ref="mediaImageRef"
                class="media-image"
                :class="{ 'is-original': imageMode === 'original' }"
                :src="originalSrc(currentMedia)"
                :alt="currentMedia.fileName"
                draggable="false"
                decoding="async"
                @load="paintImageTransform(false)"
                @click.stop
                @dragstart.prevent
              />
            </div>
          </div>
          <div v-else-if="currentMedia" class="media-video-wrap">
            <video
              ref="mediaVideoRef"
              :key="videoPlayerKey"
              class="media-video"
              :src="videoPlayUrl"
              controls
              autoplay
              playsinline
              @click.stop
            />
            <div class="media-video-toolbar" @click.stop>
              <label class="media-video-field">
                <span>清晰度</span>
                <select v-model="videoQuality" @change="onVideoQualityChange">
                  <option value="720p">720p</option>
                  <option value="1080p">1080p</option>
                  <option value="original">原片</option>
                </select>
              </label>
            </div>
          </div>

          <div
            v-if="currentMedia && currentMedia.fileType !== 2"
            class="media-toolbar"
            @click.stop
          >
            <button type="button" class="media-tool-btn" title="缩小" @click="zoomImage(-1, true)">
              <el-icon :size="18"><ZoomOut /></el-icon>
            </button>
            <span ref="imageZoomLabelRef" class="media-zoom-label">100%</span>
            <button type="button" class="media-tool-btn" title="放大" @click="zoomImage(1, true)">
              <el-icon :size="18"><ZoomIn /></el-icon>
            </button>
            <button type="button" class="media-tool-btn" title="向右旋转" @click="rotateImage">
              <el-icon :size="18"><RefreshRight /></el-icon>
            </button>
            <button type="button" class="media-tool-btn" title="切换原始尺寸" @click="toggleImageMode">
              <el-icon :size="18">
                <FullScreen v-if="imageMode === 'contain'" />
                <ScaleToOriginal v-else />
              </el-icon>
            </button>
          </div>
        </div>
      </div>
    </teleport>

    <!-- 预览顶栏按钮独立层级，打开抽屉时位置不变且可点 -->
    <teleport to="body">
      <template v-if="mediaVisible">
        <button type="button" class="media-close" title="返回" @click="closeMedia">
          <el-icon :size="22"><ArrowLeft /></el-icon>
        </button>
        <div class="media-actions">
          <button type="button" class="media-action-btn" title="下载" @click.stop="onMediaDownload">
            <el-icon :size="20"><Download /></el-icon>
          </button>
          <button
            v-if="currentMedia && currentMedia.fileType !== 2 && !isAiDraw(currentMedia) && currentMedia.scorePass === 1"
            type="button"
            class="media-action-btn"
            title="AI 出图（万相）"
            @click.stop="openDrawDialog"
          >
            <el-icon :size="20"><Brush /></el-icon>
          </button>
          <button type="button" class="media-action-btn" title="添加到..." @click.stop="onMediaAddTo">
            <el-icon :size="20"><FolderAdd /></el-icon>
          </button>
          <button type="button" class="media-action-btn" title="删除" @click.stop="onMediaDelete">
            <el-icon :size="20"><Delete /></el-icon>
          </button>
          <button
            type="button"
            class="media-action-btn"
            :class="{ active: detailOpen }"
            title="查看详细信息"
            @click.stop="onMediaDetail"
          >
            <el-icon :size="20"><InfoFilled /></el-icon>
          </button>
        </div>
      </template>
    </teleport>

    <!-- 详细信息右侧面板：避开顶部操作栏，仅在下方区域弹出 -->
    <teleport to="body">
      <div
        v-if="detailOpen && detailPhoto && !mediaVisible"
        class="photo-detail-backdrop"
        @click="closePhotoDetail"
      />
      <transition name="detail-panel">
        <aside
          v-if="detailOpen && detailPhoto"
          class="photo-detail-panel"
          @click.stop
        >
          <div class="photo-detail-head">
            <h3 class="detail-head-title">详细信息</h3>
            <button type="button" class="detail-close-btn" title="关闭" @click="closePhotoDetail">
              <el-icon :size="18"><Close /></el-icon>
            </button>
          </div>
          <div class="photo-detail-album">
            <h3 class="detail-section-title">所属相册</h3>
            <div class="detail-album-row">
              <button type="button" class="album-tag" @click="goCurrentAlbumFromDetail">
                {{ album.albumName || '相册' }}
              </button>
              <button type="button" class="album-tag ghost" @click="onDetailAddTo">添加到...</button>
            </div>
          </div>

          <div class="photo-detail-info">
            <h3 class="detail-section-title">属性</h3>
            <div class="photo-detail-list">
              <div class="photo-detail-row">
                <span class="label">出图评分</span>
                <span class="value">{{ scoreDetailText }}</span>
              </div>
              <div class="photo-detail-row">
                <span class="label">名称</span>
                <span class="value">{{ detailPhoto.fileName || '-' }}</span>
              </div>
              <div class="photo-detail-row">
                <span class="label">大小</span>
                <span class="value">{{ formatFileSize(detailPhoto.fileSize) }}</span>
              </div>
              <div class="photo-detail-row">
                <span class="label">类型</span>
                <span class="value">{{ detailPhoto.fileType === 2 ? '视频' : '图片' }}</span>
              </div>
              <div v-if="detailPhoto.fileType === 2" class="photo-detail-row">
                <span class="label">时长</span>
                <span class="value">{{ formatDuration(detailPhoto.duration) || '-' }}</span>
              </div>
              <div class="photo-detail-row">
                <span class="label">文件位置</span>
                <span class="value">相册</span>
              </div>
              <div class="photo-detail-row">
                <span class="label">文件夹位置</span>
                <span class="value" :title="detailFolderPath">{{ detailFolderPath }}</span>
              </div>
              <div class="photo-detail-row">
                <span class="label">创建时间</span>
                <span class="value">{{ formatDetailTime(detailPhoto.createTime) }}</span>
              </div>
              <div class="photo-detail-row">
                <span class="label">修改时间</span>
                <span class="value">{{ formatDetailTime(detailPhoto.updateTime || detailPhoto.shootTime || detailPhoto.createTime) }}</span>
              </div>
              <div class="photo-detail-row">
                <span class="label">拍摄时间</span>
                <span class="value">{{ formatDetailTime(detailPhoto.shootTime) }}</span>
              </div>
              <div class="photo-detail-row">
                <span class="label">相机</span>
                <span class="value">{{ detailPhoto.cameraModel || '-' }}</span>
              </div>
              <div class="photo-detail-row">
                <span class="label">镜头</span>
                <span class="value">{{ detailPhoto.lensInfo || '-' }}</span>
              </div>
              <div class="photo-detail-row">
                <span class="label">光圈</span>
                <span class="value">{{ detailPhoto.aperture || '-' }}</span>
              </div>
              <div class="photo-detail-row">
                <span class="label">快门</span>
                <span class="value">{{ detailPhoto.shutterSpeed || '-' }}</span>
              </div>
              <div class="photo-detail-row">
                <span class="label">ISO</span>
                <span class="value">{{ detailPhoto.iso ?? '-' }}</span>
              </div>
              <div class="photo-detail-row">
                <span class="label">焦距</span>
                <span class="value">{{ detailPhoto.focalLength || '-' }}</span>
              </div>
              <div class="photo-detail-row">
                <span class="label">位置</span>
                <span class="value">{{ detailLocationText }}</span>
              </div>
            </div>
          </div>
        </aside>
      </transition>
    </teleport>

    <teleport to="body">
      <el-dialog
        v-model="drawOpen"
        title="AI 出图"
        width="460px"
        append-to-body
        destroy-on-close
        class="above-media-draw-dialog"
        modal-class="above-media-draw-overlay"
        :z-index="5100"
      >
        <p class="draw-tip">
          将对 <strong>{{ drawTargetLabel }}</strong> 出图。文件会保存到本机
          <code>upload/ai-draw/</code>，并在「仅 AI 创作」中查看，不会与原片混在一起。
        </p>
        <el-form label-width="88px">
          <el-form-item label="预设风格">
            <el-select
              v-model="drawForm.preset"
              placeholder="选择预设"
              style="width: 100%"
              teleported
              popper-class="above-media-draw-select-popper"
            >
              <el-option
                v-for="p in drawPresets"
                :key="p.id"
                :label="p.label"
                :value="p.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="英文标题">
            <el-input v-model="drawForm.title" placeholder="可选，默认取地点或文件名" clearable />
          </el-form-item>
          <el-form-item v-if="selectedDrawLayout === 'FULL_WITH_TITLES'" label="英文副句">
            <el-input v-model="drawForm.subtitle" placeholder="可选，水墨海报底部小字" clearable />
          </el-form-item>
          <el-form-item v-if="selectedDrawLayout === 'TOP_PANEL_BOTTOM_PHOTO'" label="三词关键词">
            <el-input v-model="drawForm.keywords" placeholder="如 memory / light / place" clearable />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="drawOpen = false">取消</el-button>
          <el-button type="primary" :loading="drawSubmitting" @click="submitDraw">开始出图</el-button>
        </template>
      </el-dialog>
    </teleport>
  </div>
</template>

<script setup name="PhotosAlbumDetail">
import { ElMessageBox } from 'element-plus'
import { isExternal } from '@/utils/validate'
import { getToken } from '@/utils/auth'
import { saveAs } from 'file-saver'
import axios from 'axios'
import { getAlbum, updateAlbum, listAlbum, addAlbum } from '@/api/photos/album'
import { listPhoto, uploadPhoto, delPhoto, updatePhoto, listDrawPresets, drawPhoto, drawPhotoBatch, getVideoProxyStatus } from '@/api/photos/photo'
import { videoPlaySrc } from '@/utils/videoProxy'
import usePhotoScoreStore from '@/store/modules/photoScore'

const { proxy } = getCurrentInstance()
const route = useRoute()
const photoScoreStore = usePhotoScoreStore()

const loading = ref(false)
const loadingMore = ref(false)
const album = ref({})
const photoList = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(60)
/** 哨兵持续在视口内时，最多再自动连拉的页数（不含用户滚动重新进入） */
const MAX_AUTO_CHAIN = 1
let autoChainBudget = MAX_AUTO_CHAIN
/** 下滑自动加载哨兵 */
const loadMoreSentinel = ref(null)
let loadMoreObserver = null
const typeFilter = ref('all')
const scoreFilter = ref('all')
/** 内容来源：默认仅原片，AI 出图单独查看 */
const originFilter = ref('original')
/** 拍摄时间排序：desc 新→旧，asc 旧→新 */
const shootTimeOrder = ref('desc')
const sizeMode = ref('small')
/** 虚拟网格：只挂载可视区附近的格子 */
const GRID_MIN = { small: 96, medium: 140, large: 200 }
const GRID_GAP = { small: 4, medium: 8, large: 12 }
const GRID_OVERSCAN = 2
const gridWrapRef = ref(null)
const gridScrollTop = ref(0)
const gridViewHeight = ref(typeof window !== 'undefined' ? window.innerHeight : 800)
const gridWidth = ref(800)
let gridResizeObserver = null
let virtualRaf = 0
/** 顶部按钮开启的持续多选模式（等同常按 Ctrl） */
const multiMode = ref(false)
const selectedIds = ref([])
/** Shift 范围选择的锚点 */
const lastAnchorId = ref(null)
const editingDesc = ref(false)
const descDraft = ref('')
const descInputRef = ref()
const fileInputRef = ref()
const folderInputRef = ref()
const mediaVisible = ref(false)
const ctxMenu = reactive({ visible: false, x: 0, y: 0, type: 'blank', photoId: null })
const detailOpen = ref(false)
const detailPhoto = ref(null)
const createAlbumOpen = ref(false)
const creatingAlbumPage = ref(false)
const createAlbumFormRef = ref()
const createAlbumForm = reactive({
  albumName: '',
  albumDesc: '',
  isPublic: 1
})
const createAlbumRules = {
  albumName: [{ required: true, message: '请输入相册名称', trigger: 'blur' }]
}
const mediaIndex = ref(0)
const uploading = ref(false)
const uploadProgress = reactive({ done: 0, total: 0, ok: 0, fail: 0 })
const uploadItems = ref([])
/** AI 出图 */
const drawOpen = ref(false)
const drawSubmitting = ref(false)
const drawPresets = ref([])
const drawTargetIds = ref([])
const drawTargetLabel = ref('')
const drawForm = reactive({
  preset: 'ink-wash-flat',
  title: '',
  subtitle: '',
  keywords: ''
})
const selectedDrawLayout = computed(() => {
  const p = drawPresets.value.find(x => x.id === drawForm.preset)
  return p?.layout || ''
})
/** 同时上传数，避免一次打满服务器 */
const UPLOAD_CONCURRENCY = 3
/** 与 application.yml spring.servlet.multipart.max-file-size 对齐 */
const MAX_UPLOAD_BYTES = 30 * 1024 * 1024 * 1024

const uploadPercent = computed(() => {
  const items = uploadItems.value
  if (!items.length) return 0
  let loaded = 0
  let total = 0
  for (const item of items) {
    const size = Math.max(item.size || 0, 1)
    total += size
    if (item.status === 'done' || item.status === 'fail') {
      loaded += size
    } else if (item.status === 'processing') {
      // 字节已传完，等待服务端入库/生成缩略图
      loaded += size * 0.92
    } else {
      loaded += Math.min(item.loaded || 0, size) * 0.9
    }
  }
  return Math.min(100, Math.max(0, Math.round((loaded / total) * 100)))
})

const uploadCurrentName = computed(() => {
  const active = uploadItems.value.find(i => i.status === 'uploading' || i.status === 'processing')
  return active?.name || ''
})

const uploadStatusText = computed(() => {
  if (!uploading.value) return ''
  if (uploadCurrentName.value) {
    const active = uploadItems.value.find(i => i.name === uploadCurrentName.value && (i.status === 'uploading' || i.status === 'processing'))
    if (active?.status === 'processing') return `服务端处理中：${uploadCurrentName.value}`
    return `正在上传：${uploadCurrentName.value}`
  }
  if (uploadProgress.done >= uploadProgress.total) return '正在完成收尾…'
  return '准备上传…'
})

const uploadItemsVisible = computed(() => {
  // 优先展示进行中/失败，其次最近完成的，最多 6 条
  const list = [...uploadItems.value]
  const active = list.filter(i => i.status === 'uploading' || i.status === 'processing' || i.status === 'fail')
  const rest = list.filter(i => !active.includes(i)).slice(-Math.max(0, 6 - active.length))
  return [...active, ...rest].slice(0, 6)
})
const clickTimer = ref(null)
const brokenThumbs = ref(new Set())
const addToOpen = ref(false)
const albumOptions = ref([])
const targetAlbumId = ref(null)
const addingTo = ref(false)
const downloading = ref(false)
const albumLoading = ref(false)
const creatingAlbum = ref(false)
const creatingAlbumBusy = ref(false)
const newAlbumName = ref('未命名')
const newAlbumInputRef = ref()

const canSubmitAddTo = computed(() => !!targetAlbumId.value && !creatingAlbum.value && !addingTo.value)

const ctxTargetPhoto = computed(() =>
  photoList.value.find(p => p.photoId === ctxMenu.photoId) || null
)

const detailLocationText = computed(() => {
  const p = detailPhoto.value
  if (!p) return '-'
  const parts = [p.province, p.city, p.district, p.address].filter(Boolean)
  if (parts.length) return parts.join(' ')
  if (p.latitude != null && p.longitude != null) return `${p.latitude}, ${p.longitude}`
  return '-'
})

/** 磁盘上的文件夹路径（去掉文件名） */
const detailFolderPath = computed(() => {
  const raw = detailPhoto.value?.filePath
  if (!raw || typeof raw !== 'string') return '-'
  const path = raw.trim()
  if (!path) return '-'
  const norm = path.replace(/\\/g, '/')
  const idx = norm.lastIndexOf('/')
  if (idx <= 0) return path
  const dir = norm.slice(0, idx)
  return path.includes('\\') ? dir.replace(/\//g, '\\') : dir
})
const imageMode = ref('contain') // contain | original
const mediaImageRef = ref()
const mediaImageLayerRef = ref()
const imageZoomLabelRef = ref()
/** 非响应式，交互时直接改 DOM，避免 Vue 每帧重渲染导致高倍缩放卡顿 */
const imageTransform = {
  scale: 1,
  deg: 0
}
let imagePaintRaf = 0
let wheelZoomRaf = 0
let pendingWheelDelta = 0
let zoomLabelRaf = 0

const IMAGE_ZOOM_RATE = 1.2
const IMAGE_MIN_SCALE = 0.2
const IMAGE_MAX_SCALE = 4

const albumId = computed(() => route.params.albumId)

const typeFilterLabel = computed(() => {
  const map = { all: '全部', 1: '仅图片', 2: '仅视频' }
  return map[typeFilter.value] || '全部'
})

const scoreFilterLabel = computed(() => {
  const map = { all: '全部评分', pass: '仅合格', fail: '仅不合格', unscored: '未打分' }
  return map[scoreFilter.value] || '全部评分'
})

const originFilterLabel = computed(() => {
  const map = { original: '仅原片', aiDraw: '仅 AI 创作', all: '全部内容' }
  return map[originFilter.value] || '仅原片'
})

const emptyHint = computed(() => {
  if (originFilter.value === 'aiDraw') return '还没有 AI 创作。勾选合格原片后点「AI 出图」'
  if (originFilter.value === 'original') return '相册暂无原片，点击右上角添加照片'
  return '相册暂无内容，点击右上角添加照片'
})

const scoreDetailText = computed(() => {
  const p = detailPhoto.value
  if (!p || p.aestheticScore == null) return '未打分'
  const flag = p.scorePass === 1 ? '合格' : '不合格'
  const reason = p.scoreReason ? ` · ${p.scoreReason}` : ''
  return `${p.aestheticScore}（${flag}）${reason}`
})

const shootTimeOrderLabel = computed(() =>
  shootTimeOrder.value === 'asc' ? '时间升序' : '时间降序'
)

const sizeModeLabel = computed(() => {
  const map = { small: '小图模式', medium: '中图模式', large: '大图模式' }
  return map[sizeMode.value] || '小图模式'
})

const hasMore = computed(() => photoList.value.length < total.value)

const gridGap = computed(() => GRID_GAP[sizeMode.value] || 4)

const gridCols = computed(() => {
  const min = GRID_MIN[sizeMode.value] || 96
  const gap = gridGap.value
  const w = Math.max(gridWidth.value, min)
  return Math.max(1, Math.floor((w + gap) / (min + gap)))
})

const cellSize = computed(() => {
  const cols = gridCols.value
  const gap = gridGap.value
  return Math.max(1, (gridWidth.value - gap * (cols - 1)) / cols)
})

const rowStride = computed(() => cellSize.value + gridGap.value)

const totalRows = computed(() => {
  const cols = gridCols.value
  if (!cols) return 0
  return Math.ceil(photoList.value.length / cols)
})

const gridTotalHeight = computed(() => {
  const rows = totalRows.value
  if (!rows) return 0
  return rows * cellSize.value + Math.max(0, rows - 1) * gridGap.value
})

const visibleSlice = computed(() => {
  const cols = gridCols.value
  const stride = rowStride.value
  const len = photoList.value.length
  if (!len || stride <= 0 || !cols) return { start: 0, end: 0, offsetY: 0 }
  const startRow = Math.max(0, Math.floor(gridScrollTop.value / stride) - GRID_OVERSCAN)
  const endRow = Math.min(
    totalRows.value,
    Math.ceil((gridScrollTop.value + gridViewHeight.value) / stride) + GRID_OVERSCAN
  )
  return {
    start: startRow * cols,
    end: Math.min(len, endRow * cols),
    offsetY: startRow * stride
  }
})

const visiblePhotos = computed(() =>
  photoList.value.slice(visibleSlice.value.start, visibleSlice.value.end)
)

const gridWindowStyle = computed(() => ({
  transform: `translateY(${visibleSlice.value.offsetY}px)`,
  gridTemplateColumns: `repeat(${gridCols.value}, minmax(0, 1fr))`,
  gap: `${gridGap.value}px`
}))

const currentMedia = computed(() => photoList.value[mediaIndex.value] || null)

const isSelecting = computed(() => selectedIds.value.length > 0 || multiMode.value)

const isAllSelected = computed(() =>
  photoList.value.length > 0 && selectedIds.value.length >= photoList.value.length
)

const selectBtnClass = computed(() => ({
  active: selectedIds.value.length > 0,
  partial: selectedIds.value.length > 0 && !isAllSelected.value
}))

const selectBtnTitle = computed(() => {
  if (selectedIds.value.length) return '取消全选'
  return '全选'
})

function resolveUrl(url) {
  if (!url) return ''
  if (isExternal(url)) return url
  return import.meta.env.VITE_APP_BASE_API + url
}

function canShowThumb(item) {
  if (!item || brokenThumbs.value.has(item.photoId)) return false
  if (item.thumbUrl) return true
  // 视频无静态封面时走按需截帧接口
  return item.fileType === 2 && !!item.photoId
}

function thumbSrc(item) {
  if (!item) return ''
  if (item.thumbUrl) return resolveUrl(item.thumbUrl)
  if (item.fileType === 2 && item.photoId) {
    return resolveUrl('/album/photo/thumb/' + item.photoId)
  }
  return ''
}

function originalSrc(item) {
  if (!item) return ''
  return resolveUrl('/album/photo/media/' + item.photoId + '?original=true')
}

/** 视频浏览默认 1080p30；无浏览档则直接播原片 */
const videoQuality = ref('1080p')
const VIDEO_PLAY_FPS = 30
const videoPlayUrl = ref('')
const videoPlayerKey = ref('')
const mediaVideoRef = ref()
let videoProxyReqSeq = 0
let pendingVideoSeek = null

function captureVideoTime() {
  const el = mediaVideoRef.value
  if (el && Number.isFinite(el.currentTime) && el.currentTime > 0.2) {
    pendingVideoSeek = el.currentTime
  }
}

function applyPendingSeek() {
  const el = mediaVideoRef.value
  if (!el || pendingVideoSeek == null) return
  const t = pendingVideoSeek
  pendingVideoSeek = null
  const onMeta = () => {
    try {
      el.currentTime = t
    } catch (_) { /* ignore */ }
  }
  if (el.readyState >= 1) onMeta()
  else el.addEventListener('loadedmetadata', onMeta, { once: true })
}

function playOriginalNow(photoId) {
  videoPlayUrl.value = videoPlaySrc(photoId, 'original')
  videoPlayerKey.value = `${photoId}-original-fallback`
  nextTick(() => applyPendingSeek())
}

async function reloadVideoProxy() {
  const item = currentMedia.value
  if (!item || item.fileType !== 2 || !item.photoId) {
    videoPlayUrl.value = ''
    return
  }
  const seq = ++videoProxyReqSeq
  const quality = videoQuality.value

  if (quality === 'original') {
    videoPlayUrl.value = videoPlaySrc(item.photoId, 'original')
    videoPlayerKey.value = `${item.photoId}-original`
    nextTick(() => applyPendingSeek())
    return
  }

  try {
    const res = await getVideoProxyStatus(item.photoId, quality, VIDEO_PLAY_FPS)
    if (seq !== videoProxyReqSeq) return
    const data = res?.data || res || {}
    if (data.status === 'ready') {
      videoPlayUrl.value = videoPlaySrc(item.photoId, quality, VIDEO_PLAY_FPS)
      videoPlayerKey.value = `${item.photoId}-${quality}-30-${data.fileSize || 0}`
      nextTick(() => applyPendingSeek())
      return
    }
  } catch (_) {
    /* 查状态失败则播原片 */
  }
  if (seq !== videoProxyReqSeq) return
  playOriginalNow(item.photoId)
}

function onVideoQualityChange() {
  captureVideoTime()
  reloadVideoProxy()
}

/** 秒 -> 0:16 / 1:02:03 */
function formatDuration(seconds) {
  if (seconds == null || seconds === '' || Number.isNaN(Number(seconds))) return ''
  const total = Math.max(0, Math.round(Number(seconds)))
  const h = Math.floor(total / 3600)
  const m = Math.floor((total % 3600) / 60)
  const s = total % 60
  const ss = String(s).padStart(2, '0')
  if (h > 0) return `${h}:${String(m).padStart(2, '0')}:${ss}`
  return `${m}:${ss}`
}

function onThumbError(item) {
  if (!item?.photoId || brokenThumbs.value.has(item.photoId)) return
  // 失败只占位，绝不回退原图
  const next = new Set(brokenThumbs.value)
  next.add(item.photoId)
  brokenThumbs.value = next
}

function updateVirtualMetrics() {
  const el = gridWrapRef.value
  if (!el) return
  const rect = el.getBoundingClientRect()
  gridWidth.value = el.clientWidth || rect.width || gridWidth.value
  gridViewHeight.value = window.innerHeight
  gridScrollTop.value = Math.max(0, -rect.top)
}

function scheduleVirtualUpdate() {
  if (virtualRaf) return
  virtualRaf = requestAnimationFrame(() => {
    virtualRaf = 0
    updateVirtualMetrics()
  })
}

function setupGridResizeObserver() {
  teardownGridResizeObserver()
  const el = gridWrapRef.value
  if (!el || typeof ResizeObserver === 'undefined') return
  gridResizeObserver = new ResizeObserver(() => scheduleVirtualUpdate())
  gridResizeObserver.observe(el)
}

function teardownGridResizeObserver() {
  if (gridResizeObserver) {
    gridResizeObserver.disconnect()
    gridResizeObserver = null
  }
}

function goBack() {
  // 返回列表：原地切回相册，不增删页签
  proxy.$tab.navigatePage({ path: '/photos/index' })
}

function openPhotoMap() {
  proxy.$tab.navigatePage({
    path: '/photos/map',
    query: { albumId: albumId.value }
  })
}

function handleFilterType(cmd) {
  typeFilter.value = cmd
  reload()
}

function handleScoreFilter(cmd) {
  scoreFilter.value = cmd
  reload()
}

function handleOriginFilter(cmd) {
  originFilter.value = cmd
  reload()
}

function isAiDraw(item) {
  if (!item) return false
  if (item.originType === 'ai_draw') return true
  return typeof item.remark === 'string' && item.remark.startsWith('AI出图:')
}

function collectDrawCandidates(ids) {
  const idSet = new Set(ids)
  return photoList.value.filter(p =>
    idSet.has(p.photoId) &&
    p.fileType !== 2 &&
    !isAiDraw(p) &&
    p.scorePass === 1
  )
}

function prepareDrawDialog(targets) {
  if (!targets.length) {
    proxy.$modal.msgWarning('没有可出图的照片：请选择已打分合格的原片（AI 创作不能再次出图）')
    return false
  }
  drawTargetIds.value = targets.map(p => p.photoId)
  drawTargetLabel.value = targets.length === 1
    ? (targets[0].fileName || '1 张照片')
    : `${targets.length} 张合格原片`
  const first = targets[0]
  drawForm.preset = drawPresets.value[0]?.id || 'ink-wash-flat'
  drawForm.title = first.city || first.district || ''
  drawForm.subtitle = ''
  drawForm.keywords = ''
  return true
}

async function openDrawDialogForPhotos(targets) {
  await ensureDrawPresets()
  if (!prepareDrawDialog(targets)) return
  drawOpen.value = true
}

async function openDrawDialog() {
  const item = currentMedia.value
  if (!item || item.fileType === 2) return
  const targets = collectDrawCandidates([item.photoId])
  await openDrawDialogForPhotos(targets)
}

async function openDrawDialogFromToolbar() {
  let targets = []
  if (selectedIds.value.length) {
    targets = collectDrawCandidates(selectedIds.value)
  } else if (currentMedia.value && mediaVisible.value) {
    targets = collectDrawCandidates([currentMedia.value.photoId])
  } else {
    proxy.$modal.msgWarning('请先勾选照片，或在预览中打开一张合格原片')
    return
  }
  await openDrawDialogForPhotos(targets)
}

async function openDrawDialogFromSelection() {
  const targets = collectDrawCandidates(selectedIds.value)
  await openDrawDialogForPhotos(targets)
}

function onItemCtxDraw() {
  const item = ctxTargetPhoto.value
  closeCtxMenu()
  if (!item) return
  openDrawDialogForPhotos(collectDrawCandidates([item.photoId]))
}

async function runPhotoScore() {
  const selected = selectedIds.value.filter(Boolean)
  const targetHint = selected.length
    ? `已选的 ${selected.length} 张`
    : '本相册尚未打分的照片'
  try {
    await ElMessageBox.confirm(
      `将为${targetHint}做本地质量打分（清晰度/曝光/对比等），满分 100，默认 70 分以上才允许图生图。是否继续？`,
      '质量打分',
      { type: 'info', confirmButtonText: '开始打分', cancelButtonText: '取消' }
    )
  } catch (e) {
    return
  }
  const payload = { force: false }
  if (selected.length) {
    payload.photoIds = selected
  }
  try {
    await photoScoreStore.start(albumId.value, payload)
    proxy.$modal.msgSuccess('已开始后台打分，可在右下角查看进度')
  } catch (e) {
    /* request 已提示 */
  }
}

async function ensureDrawPresets() {
  if (drawPresets.value.length) return
  try {
    const res = await listDrawPresets()
    drawPresets.value = res.data || []
    if (drawPresets.value.length && !drawForm.preset) {
      drawForm.preset = drawPresets.value[0].id
    }
  } catch (e) {
    drawPresets.value = [
      { id: 'ink-wash-flat', label: '水墨扁平重构' },
      { id: 'travel-poster', label: '旅行摄影海报 3:4' },
      { id: 'minimal-zine', label: '极简 Zine 海报' },
      { id: 'photo-diptych', label: '摄影+抽象双联（上下）' },
      { id: 'photo-diptych-column', label: '摄影+抽象双列' },
      { id: 'photo-abstract', label: '摄影+抽象编辑' },
      { id: 'scene-to-art', label: '场景蒸馏艺术' },
      { id: 'rdr2-journal', label: '荒野大镖客2 · 日记炭笔' },
      { id: 'photo-relic', label: 'Photo Relic 编辑' }
    ]
  }
}

async function submitDraw() {
  if (!drawTargetIds.value.length) return
  if (!drawForm.preset) {
    proxy.$modal.msgWarning('请选择预设风格')
    return
  }
  drawSubmitting.value = true
  const payload = { preset: drawForm.preset, photoIds: drawTargetIds.value.slice() }
  if (drawForm.title?.trim()) payload.title = drawForm.title.trim()
  if (drawForm.subtitle?.trim()) payload.subtitle = drawForm.subtitle.trim()
  if (drawForm.keywords?.trim()) payload.keywords = drawForm.keywords.trim()
  try {
    let data
    if (drawTargetIds.value.length === 1) {
      const single = { ...payload }
      delete single.photoIds
      const res = await drawPhoto(drawTargetIds.value[0], single)
      data = { success: 1, failed: 0, total: 1, items: [{ ok: true, result: res.data }] }
    } else {
      const res = await drawPhotoBatch(albumId.value, payload)
      data = res.data || {}
    }
    drawOpen.value = false
    if (mediaVisible.value) closeMedia()
    const ok = data.success || 0
    const fail = data.failed || 0
    if (fail > 0) {
      proxy.$modal.msgWarning(`出图完成：成功 ${ok}，失败 ${fail}。可在「仅 AI 创作」中查看成品。`)
    } else {
      proxy.$modal.msgSuccess(`出图成功 ${ok} 张，已保存到本机并在「仅 AI 创作」中查看。`)
    }
    if (ok > 0 && originFilter.value === 'original') {
      originFilter.value = 'aiDraw'
    }
    await reload()
  } catch (e) {
    /* request 已提示 */
  } finally {
    drawSubmitting.value = false
  }
}

function handleShootTimeOrder(cmd) {
  if (cmd !== 'asc' && cmd !== 'desc') return
  shootTimeOrder.value = cmd
  reload()
}

function handleSizeMode(cmd) {
  sizeMode.value = cmd
  nextTick(() => scheduleVirtualUpdate())
}

function isSelected(id) {
  return selectedIds.value.includes(id)
}

function clearSelection() {
  selectedIds.value = []
  lastAnchorId.value = null
}

function selectAllLoaded() {
  const ids = photoList.value.map(p => p.photoId)
  selectedIds.value = ids.slice()
  lastAnchorId.value = ids.length ? ids[0] : null
  multiMode.value = true
}

async function ensureAllLoaded() {
  while (photoList.value.length < total.value) {
    pageNum.value += 1
    await loadPhotos(false)
  }
}

async function onSelectHeaderClick() {
  if (selectedIds.value.length) {
    clearSelection()
    multiMode.value = false
    return
  }
  if (!total.value) {
    proxy.$modal.msg('暂无可选项')
    return
  }
  if (photoList.value.length < total.value) {
    loading.value = true
    try {
      await ensureAllLoaded()
    } finally {
      loading.value = false
    }
  }
  selectAllLoaded()
}

function startEditDesc() {
  if (editingDesc.value) return
  descDraft.value = album.value.albumDesc || ''
  editingDesc.value = true
  nextTick(() => {
    descInputRef.value?.focus?.()
  })
}

function saveDesc() {
  if (!editingDesc.value) return
  editingDesc.value = false
  const next = (descDraft.value || '').trim()
  if (next === (album.value.albumDesc || '')) return
  updateAlbum({
    albumId: album.value.albumId,
    albumName: album.value.albumName,
    albumDesc: next,
    isPublic: album.value.isPublic,
    coverUrl: album.value.coverUrl
  }).then(() => {
    album.value.albumDesc = next
    proxy.$modal.msgSuccess('描述已更新')
  })
}

function isMediaFile(file) {
  const type = file?.type || ''
  if (type.startsWith('image/') || type.startsWith('video/')) return true
  const name = String(file?.name || '').toLowerCase()
  return /\.(jpe?g|png|gif|webp|bmp|heic|heif|mp4|mov|avi|mkv|webm|m4v)$/i.test(name)
}

function triggerUpload() {
  closeCtxMenu()
  fileInputRef.value?.click?.()
}

function triggerFolderUpload() {
  closeCtxMenu()
  folderInputRef.value?.click?.()
}

function formatUploadSize(bytes) {
  const n = Number(bytes) || 0
  if (n < 1024) return `${n} B`
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`
  if (n < 1024 * 1024 * 1024) return `${(n / 1024 / 1024).toFixed(1)} MB`
  return `${(n / 1024 / 1024 / 1024).toFixed(2)} GB`
}

function uploadItemLabel(item) {
  if (item.status === 'done') return '完成'
  if (item.status === 'fail') return '失败'
  if (item.status === 'processing') return '处理中'
  if (item.status === 'uploading') {
    const size = Math.max(item.size || 0, 1)
    const pct = Math.min(100, Math.round(((item.loaded || 0) / size) * 100))
    return `${pct}%`
  }
  return formatUploadSize(item.size)
}

function runWithConcurrency(items, worker, concurrency) {
  let index = 0
  const runners = Array.from({ length: Math.min(concurrency, items.length) }, async () => {
    while (index < items.length) {
      const current = index++
      await worker(items[current], current)
    }
  })
  return Promise.all(runners)
}

async function onFilesSelected(e) {
  const raw = Array.from(e.target.files || [])
  e.target.value = ''
  const media = raw.filter(isMediaFile)
  if (!media.length) {
    if (raw.length) proxy.$modal.msgWarning('所选内容中没有可上传的照片或视频')
    return
  }
  if (uploading.value) {
    proxy.$modal.msgWarning('仍有上传任务进行中，请稍候')
    return
  }

  const oversized = media.filter((f) => (f.size || 0) > MAX_UPLOAD_BYTES)
  const files = media.filter((f) => (f.size || 0) <= MAX_UPLOAD_BYTES)
  if (oversized.length) {
    const sample = oversized.slice(0, 3).map((f) => `${f.name}（${formatUploadSize(f.size)}）`).join('、')
    const more = oversized.length > 3 ? ` 等 ${oversized.length} 个` : ''
    proxy.$modal.msgWarning(
      `已跳过超限文件：${sample}${more}。单文件上限约 30GB；本地超大视频请用「扫描入库」。`
    )
  }
  if (!files.length) return

  uploading.value = true
  uploadProgress.done = 0
  uploadProgress.ok = 0
  uploadProgress.fail = 0
  uploadProgress.total = files.length
  uploadItems.value = files.map((file, id) => ({
    id,
    name: file.name,
    size: file.size || 0,
    loaded: 0,
    status: 'pending',
    file
  }))

  await runWithConcurrency(uploadItems.value, async (item) => {
    item.status = 'uploading'
    item.loaded = 0
    const form = new FormData()
    form.append('file', item.file)
    form.append('albumId', albumId.value)
    try {
      await uploadPhoto(form, {
        showActionLoading: false,
        onUploadProgress: (evt) => {
          const total = evt.total || item.size || 0
          if (total > 0) {
            item.size = total
            item.loaded = evt.loaded || 0
            if (evt.loaded >= total) {
              item.status = 'processing'
            }
          }
        }
      })
      item.loaded = item.size || item.loaded
      item.status = 'done'
      uploadProgress.ok += 1
    } catch (err) {
      item.status = 'fail'
      uploadProgress.fail += 1
    } finally {
      uploadProgress.done += 1
      item.file = null
    }
  }, UPLOAD_CONCURRENCY)

  const ok = uploadProgress.ok
  const fail = uploadProgress.fail
  if (ok) proxy.$modal.msgSuccess(`成功添加 ${ok} 个文件${fail ? `，失败 ${fail}` : ''}`)
  else proxy.$modal.msgError('添加失败')
  if (ok) reload()
  uploading.value = false
  uploadItems.value = []
  uploadProgress.done = 0
  uploadProgress.total = 0
  uploadProgress.ok = 0
  uploadProgress.fail = 0
}

function closeCtxMenu() {
  ctxMenu.visible = false
  ctxMenu.type = 'blank'
  ctxMenu.photoId = null
}

function positionCtxMenu(e, menuW = 220, menuH = 220) {
  const pad = 8
  let x = e.clientX
  let y = e.clientY
  if (x + menuW + pad > window.innerWidth) x = window.innerWidth - menuW - pad
  if (y + menuH + pad > window.innerHeight) y = window.innerHeight - menuH - pad
  ctxMenu.x = Math.max(pad, x)
  ctxMenu.y = Math.max(pad, y)
  ctxMenu.visible = true
}

function onPageContextMenu(e) {
  e.preventDefault()
  const t = e.target
  // 仅空白区域弹出；点在缩略图/控件/弹层上不弹出
  if (
    t.closest?.(
      '.photo-cell, .detail-header, .detail-toolbar, .selection-bar, .photos-ctx-menu, .el-dialog, .el-overlay, .media-viewer, button, a, input, textarea, .el-button, .el-dropdown'
    )
  ) {
    closeCtxMenu()
    return
  }
  ctxMenu.type = 'blank'
  ctxMenu.photoId = null
  positionCtxMenu(e, 220, 200)
}

function onItemContextMenu(item, e) {
  // 未选中则单选当前项；已在多选中则保持选中集合
  if (!isSelected(item.photoId)) {
    selectedIds.value = [item.photoId]
    lastAnchorId.value = item.photoId
  }
  ctxMenu.type = 'item'
  ctxMenu.photoId = item.photoId
  positionCtxMenu(e, 200, 280)
}

function formatFileSize(size) {
  const n = Number(size)
  if (!n || Number.isNaN(n)) return '-'
  if (n < 1024) return `${n} B`
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`
  if (n < 1024 * 1024 * 1024) return `${(n / 1024 / 1024).toFixed(1)} MB`
  return `${(n / 1024 / 1024 / 1024).toFixed(2)} GB`
}

function onItemCtxDownload() {
  closeCtxMenu()
  downloadSelected()
}

function onItemCtxAddTo() {
  closeCtxMenu()
  openAddToAlbum()
}

function onItemCtxDetail() {
  const item = ctxTargetPhoto.value
  closeCtxMenu()
  if (!item) return
  openPhotoDetail(item)
}

function formatDetailTime(time) {
  if (!time) return '-'
  const d = new Date(typeof time === 'string' ? time.replace(/-/g, '/') : time)
  if (Number.isNaN(d.getTime())) return String(time)
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}/${pad(d.getMonth() + 1)}/${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function openPhotoDetail(item) {
  if (!item) return
  detailPhoto.value = item
  detailOpen.value = true
}

function closePhotoDetail() {
  detailOpen.value = false
}

function ensureMediaSelected() {
  const item = currentMedia.value
  if (!item) return null
  selectedIds.value = [item.photoId]
  lastAnchorId.value = item.photoId
  return item
}

function onMediaDownload() {
  if (!ensureMediaSelected()) return
  downloadSelected()
}

function confirmAboveMedia(content) {
  // 预览层 z-index=3000，确认框需更高，否则会点了没反应
  return ElMessageBox.confirm(content, '系统提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
    customClass: 'above-media-msgbox',
    appendTo: document.body
  })
}

function removePhotosFromViewer(ids) {
  const idSet = new Set(ids.map(String))
  const oldIdx = mediaIndex.value
  photoList.value = photoList.value.filter(p => !idSet.has(String(p.photoId)))
  total.value = Math.max(0, (total.value || 0) - ids.length)
  selectedIds.value = selectedIds.value.filter(x => !idSet.has(String(x)))
  detailOpen.value = false
  if (!photoList.value.length) {
    closeMedia()
    return
  }
  mediaIndex.value = Math.min(oldIdx, photoList.value.length - 1)
  resetImageTransform()
}

function onMediaAddTo() {
  const item = ensureMediaSelected()
  if (!item) {
    proxy.$modal.msgWarning('当前没有可添加的内容')
    return
  }
  openAddToAlbum()
}

function onMediaDetail() {
  if (detailOpen.value && detailPhoto.value?.photoId === currentMedia.value?.photoId) {
    closePhotoDetail()
    return
  }
  openPhotoDetail(currentMedia.value)
}

function onDetailAddTo() {
  const item = detailPhoto.value || currentMedia.value
  if (!item) return
  selectedIds.value = [item.photoId]
  lastAnchorId.value = item.photoId
  openAddToAlbum()
}

function goCurrentAlbumFromDetail() {
  closePhotoDetail()
  if (mediaVisible.value) closeMedia()
}

function onMediaDelete() {
  const item = currentMedia.value
  if (!item) {
    proxy.$modal.msgWarning('当前没有可删除的内容')
    return
  }
  const id = item.photoId
  confirmAboveMedia('确认将当前项放入回收站吗？')
    .then(() => delPhoto(String(id)))
    .then(() => {
      proxy.$modal.msgSuccess('已放入回收站')
      removePhotosFromViewer([id])
    })
    .catch(() => {})
}

function onItemCtxSetCover() {
  const item = ctxTargetPhoto.value
  closeCtxMenu()
  if (!item) return
  // 优先静态缩略图路径，列表封面不再经 media 接口
  const coverUrl = item.thumbUrl || `/album/photo/media/${item.photoId}`
  updateAlbum({
    albumId: album.value.albumId,
    albumName: album.value.albumName,
    albumDesc: album.value.albumDesc,
    isPublic: album.value.isPublic,
    coverUrl
  }).then(() => {
    album.value.coverUrl = coverUrl
    proxy.$modal.msgSuccess('已设置为相册封面')
  })
}

function deletePhotosByIds(ids, confirmText, successText) {
  if (!ids.length) return
  proxy.$modal.confirm(confirmText)
    .then(() => delPhoto(ids.join(',')))
    .then(() => {
      proxy.$modal.msgSuccess(successText)
      cancelMultiSelect()
      reload()
    })
    .catch(() => {})
}

function onItemCtxRemoveFromAlbum() {
  const ids = selectedIds.value.slice()
  closeCtxMenu()
  deletePhotosByIds(
    ids,
    `确认将选中的 ${ids.length} 项放入回收站吗？`,
    '已放入回收站'
  )
}

function onItemCtxTrash() {
  const ids = selectedIds.value.slice()
  closeCtxMenu()
  deletePhotosByIds(
    ids,
    `确认将选中的 ${ids.length} 项放入回收站吗？`,
    '已放入回收站'
  )
}

function onCtxUploadFiles() {
  triggerUpload()
}

function onCtxUploadFolder() {
  triggerFolderUpload()
}

function onCtxCreateAlbum() {
  closeCtxMenu()
  createAlbumForm.albumName = ''
  createAlbumForm.albumDesc = ''
  createAlbumForm.isPublic = 1
  createAlbumOpen.value = true
}

function onCtxRefresh() {
  closeCtxMenu()
  init()
}

function resetCreateAlbumForm() {
  createAlbumForm.albumName = ''
  createAlbumForm.albumDesc = ''
  createAlbumForm.isPublic = 1
  createAlbumFormRef.value?.resetFields?.()
}

function submitCreateAlbum() {
  createAlbumFormRef.value?.validate?.(valid => {
    if (!valid) return
    creatingAlbumPage.value = true
    addAlbum({
      albumName: createAlbumForm.albumName.trim(),
      albumDesc: createAlbumForm.albumDesc,
      isPublic: createAlbumForm.isPublic,
      photoCount: 0,
      sortOrder: 0
    })
      .then(res => {
        const created = res.data || {}
        proxy.$modal.msgSuccess('创建成功')
        createAlbumOpen.value = false
        if (created.albumId != null) {
          proxy.$tab.navigatePage({ path: '/photos/detail/' + created.albumId })
        } else {
          proxy.$tab.navigatePage({ path: '/photos/index' })
        }
      })
      .finally(() => {
        creatingAlbumPage.value = false
      })
  })
}

function applySelection(item, event = {}) {
  const id = item.photoId
  const ids = photoList.value.map(p => p.photoId)
  const idx = ids.indexOf(id)
  if (idx < 0) return

  const ctrl = !!(event.ctrlKey || event.metaKey || multiMode.value)
  const shift = !!event.shiftKey

  if (shift && lastAnchorId.value != null) {
    const from = ids.indexOf(lastAnchorId.value)
    if (from >= 0) {
      const start = Math.min(from, idx)
      const end = Math.max(from, idx)
      const range = ids.slice(start, end + 1)
      if (ctrl) {
        selectedIds.value = [...new Set(selectedIds.value.concat(range))]
      } else {
        selectedIds.value = range.slice()
      }
      return
    }
  }

  if (ctrl) {
    const i = selectedIds.value.indexOf(id)
    if (i >= 0) selectedIds.value.splice(i, 1)
    else selectedIds.value.push(id)
    lastAnchorId.value = id
    return
  }

  // 默认单选：再次点击已选项则取消
  if (selectedIds.value.length === 1 && selectedIds.value[0] === id) {
    clearSelection()
  } else {
    selectedIds.value = [id]
    lastAnchorId.value = id
  }
}

function onItemClick(item, event) {
  const immediate = event.ctrlKey || event.metaKey || event.shiftKey || multiMode.value
  if (clickTimer.value) clearTimeout(clickTimer.value)
  if (immediate) {
    applySelection(item, event)
    return
  }
  clickTimer.value = setTimeout(() => {
    applySelection(item, event)
    clickTimer.value = null
  }, 200)
}

function onItemDblClick(item) {
  if (clickTimer.value) {
    clearTimeout(clickTimer.value)
    clickTimer.value = null
  }
  // 双击查看时取消多选/全选状态
  clearSelection()
  multiMode.value = false
  openViewer(item)
}

function buildImageTransformCss() {
  const { scale, deg } = imageTransform
  // 仅缩放/旋转，始终由 flex 居中，不允许平移拖动
  return `translate3d(0,0,0) scale(${scale}) rotate(${deg}deg)`
}

function syncZoomLabel() {
  const el = imageZoomLabelRef.value
  if (!el) return
  el.textContent = `${Math.round(imageTransform.scale * 100)}%`
}

function scheduleZoomLabel() {
  if (zoomLabelRaf) return
  zoomLabelRaf = requestAnimationFrame(() => {
    zoomLabelRaf = 0
    syncZoomLabel()
  })
}

function paintImageTransform(animate = false) {
  const el = mediaImageLayerRef.value
  if (!el) return
  el.style.transition = animate ? 'transform .2s ease-out' : 'none'
  el.style.transform = buildImageTransformCss()
  scheduleZoomLabel()
}

function schedulePaintImage(animate = false) {
  if (imagePaintRaf) cancelAnimationFrame(imagePaintRaf)
  imagePaintRaf = requestAnimationFrame(() => {
    imagePaintRaf = 0
    paintImageTransform(animate)
  })
}

function resetImageTransform() {
  imageMode.value = 'contain'
  imageTransform.scale = 1
  imageTransform.deg = 0
  pendingWheelDelta = 0
  if (wheelZoomRaf) {
    cancelAnimationFrame(wheelZoomRaf)
    wheelZoomRaf = 0
  }
  nextTick(() => {
    paintImageTransform(false)
    syncZoomLabel()
  })
}

function openViewer(item) {
  const idx = photoList.value.findIndex(p => p.photoId === item.photoId)
  mediaIndex.value = idx >= 0 ? idx : 0
  resetImageTransform()
  videoQuality.value = '1080p'
  pendingVideoSeek = null
  mediaVisible.value = true
  nextTick(() => reloadVideoProxy())
}

function closeMedia() {
  detailOpen.value = false
  mediaVisible.value = false
  videoPlayUrl.value = ''
  resetImageTransform()
}

function shiftMedia(step) {
  const next = mediaIndex.value + step
  if (next < 0 || next >= photoList.value.length) return
  mediaIndex.value = next
  resetImageTransform()
  videoQuality.value = '1080p'
  pendingVideoSeek = null
  nextTick(() => reloadVideoProxy())
}

function zoomImage(delta, animate = false) {
  const next = delta > 0
    ? imageTransform.scale * IMAGE_ZOOM_RATE
    : imageTransform.scale / IMAGE_ZOOM_RATE
  imageTransform.scale = Math.min(IMAGE_MAX_SCALE, Math.max(IMAGE_MIN_SCALE, next))
  if (animate) paintImageTransform(true)
  else schedulePaintImage(false)
}

function rotateImage() {
  imageTransform.deg += 90
  paintImageTransform(true)
}

function toggleImageMode() {
  if (imageMode.value === 'contain') {
    imageMode.value = 'original'
    imageTransform.scale = 1
  } else {
    imageMode.value = 'contain'
    imageTransform.scale = 1
  }
  nextTick(() => paintImageTransform(false))
}

function onImageWheel(e) {
  pendingWheelDelta += e.deltaY < 0 ? 1 : -1
  if (wheelZoomRaf) return
  wheelZoomRaf = requestAnimationFrame(() => {
    wheelZoomRaf = 0
    const delta = pendingWheelDelta
    pendingWheelDelta = 0
    if (!delta) return
    zoomImage(delta > 0 ? 1 : -1, false)
  })
}

function onImageDblClick(e) {
  e.preventDefault()
  // 清除双击产生的文本选区，避免白底竖线残影
  window.getSelection?.()?.removeAllRanges?.()
  toggleImageMode()
}

function cancelMultiSelect() {
  clearSelection()
  multiMode.value = false
}

function selectedPhotos() {
  const idSet = new Set(selectedIds.value)
  return photoList.value.filter(p => idSet.has(p.photoId))
}

async function downloadSelected() {
  const items = selectedPhotos()
  if (!items.length || downloading.value) return
  downloading.value = true
  try {
    for (const item of items) {
      const url = originalSrc(item)
      const res = await axios({
        method: 'get',
        url,
        responseType: 'blob',
        headers: { Authorization: 'Bearer ' + getToken() }
      })
      const name = item.fileName || `photo_${item.photoId}`
      saveAs(res.data, name)
    }
    if (items.length > 1) {
      proxy.$modal.msgSuccess(`已开始下载 ${items.length} 项`)
    }
  } catch (e) {
    console.error(e)
    proxy.$modal.msgError('下载失败')
  } finally {
    downloading.value = false
  }
}

function openAddToAlbum() {
  if (!selectedIds.value.length) return
  targetAlbumId.value = null
  addToOpen.value = true
}

function resetAddToState() {
  targetAlbumId.value = null
  creatingAlbum.value = false
  creatingAlbumBusy.value = false
  newAlbumName.value = '未命名'
  albumOptions.value = []
}

function albumCoverSrc(item) {
  if (!item) return ''
  if (item.coverUrl) return resolveUrl(item.coverUrl)
  if (item.photoCount > 0 && item.coverPhotoId) {
    return resolveUrl('/album/photo/media/' + item.coverPhotoId)
  }
  return ''
}

function onAddToOpen() {
  loadAlbumOptions()
}

function loadAlbumOptions() {
  albumLoading.value = true
  return listAlbum({ pageNum: 1, pageSize: 500 })
    .then(res => {
      const rows = res.rows || res.data || []
      albumOptions.value = rows.filter(a => String(a.albumId) !== String(albumId.value))
    })
    .finally(() => {
      albumLoading.value = false
    })
}

function selectTargetAlbum(item) {
  if (creatingAlbum.value) cancelCreateAlbum()
  targetAlbumId.value = item.albumId
}

function startCreateAlbum() {
  if (creatingAlbum.value) {
    nextTick(() => newAlbumInputRef.value?.focus?.())
    return
  }
  creatingAlbum.value = true
  newAlbumName.value = '未命名'
  targetAlbumId.value = null
  nextTick(() => {
    const input = newAlbumInputRef.value
    if (!input) return
    input.focus?.()
    input.select?.()
  })
}

function cancelCreateAlbum() {
  creatingAlbum.value = false
  creatingAlbumBusy.value = false
  newAlbumName.value = '未命名'
}

async function confirmCreateAlbum() {
  if (creatingAlbumBusy.value) return
  const name = (newAlbumName.value || '').trim() || '未命名'
  creatingAlbumBusy.value = true
  try {
    const res = await addAlbum({
      albumName: name,
      albumDesc: '',
      isPublic: 1,
      photoCount: 0,
      sortOrder: 0
    })
    const created = res.data || {}
    await loadAlbumOptions()
    if (created.albumId != null) {
      if (!albumOptions.value.some(a => String(a.albumId) === String(created.albumId))) {
        albumOptions.value.unshift({
          albumId: created.albumId,
          albumName: created.albumName || name,
          photoCount: created.photoCount ?? 0,
          coverUrl: created.coverUrl
        })
      }
      targetAlbumId.value = created.albumId
    }
    creatingAlbum.value = false
    proxy.$modal.msgSuccess('相册已创建')
  } catch (e) {
    console.error(e)
    proxy.$modal.msgError('创建相册失败')
  } finally {
    creatingAlbumBusy.value = false
  }
}

async function submitAddToAlbum() {
  if (!canSubmitAddTo.value) return
  const ids = selectedIds.value.slice()
  if (!ids.length) return
  const stayInViewer = mediaVisible.value
  addingTo.value = true
  try {
    for (const photoId of ids) {
      const item = photoList.value.find(p => String(p.photoId) === String(photoId))
      await updatePhoto({
        photoId,
        albumId: targetAlbumId.value,
        fileName: item?.fileName
      })
    }
    proxy.$modal.msgSuccess(`已添加 ${ids.length} 项`)
    addToOpen.value = false
    cancelMultiSelect()
    if (stayInViewer) {
      // 已移到其他相册，从当前预览列表移除
      removePhotosFromViewer(ids)
    } else {
      reload()
    }
  } catch (e) {
    console.error(e)
    proxy.$modal.msgError('添加失败')
  } finally {
    addingTo.value = false
  }
}

function removeSelected() {
  if (!selectedIds.value.length) return
  proxy.$modal.confirm(`确认将选中的 ${selectedIds.value.length} 项放入回收站吗？`)
    .then(() => delPhoto(selectedIds.value.join(',')))
    .then(() => {
      proxy.$modal.msgSuccess('已放入回收站')
      cancelMultiSelect()
      reload()
    })
    .catch(() => {})
}

function loadAlbum() {
  return getAlbum(albumId.value)
    .then(res => {
      album.value = res.data || {}
    })
    .catch(() => {
      proxy.$modal.msgError('相册不存在或已放入回收站')
      proxy.$tab.navigatePage({ path: '/photos/index' })
    })
}

function refillAutoChainBudget() {
  autoChainBudget = MAX_AUTO_CHAIN
}

function loadPhotos(reset = false) {
  if (reset) {
    pageNum.value = 1
    loading.value = true
    refillAutoChainBudget()
    brokenThumbs.value = new Set()
  } else {
    loadingMore.value = true
  }
  const query = {
    pageNum: pageNum.value,
    pageSize: pageSize.value,
    albumId: albumId.value,
    shootTimeOrder: shootTimeOrder.value
  }
  if (typeFilter.value !== 'all') {
    query.fileType = Number(typeFilter.value)
  }
  if (scoreFilter.value !== 'all') {
    query.scoreFilter = scoreFilter.value
  }
  if (originFilter.value !== 'all') {
    query.originFilter = originFilter.value
  }
  return listPhoto(query)
    .then(res => {
      const rows = res.rows || []
      total.value = res.total || 0
      photoList.value = reset ? rows : photoList.value.concat(rows)
    })
    .finally(() => {
      loading.value = false
      loadingMore.value = false
      nextTick(() => {
        scheduleVirtualUpdate()
        setupGridResizeObserver()
      })
    })
}

/** @param {boolean} force 手动点击「加载更多」时绕过连拉额度 */
function loadMore(force = false) {
  if (!hasMore.value || loadingMore.value || loading.value) return
  if (!force) {
    if (autoChainBudget <= 0) return
    autoChainBudget -= 1
  }
  pageNum.value += 1
  loadPhotos(false)
}

function teardownLoadMoreObserver() {
  if (loadMoreObserver) {
    loadMoreObserver.disconnect()
    loadMoreObserver = null
  }
}

function setupLoadMoreObserver() {
  teardownLoadMoreObserver()
  const el = loadMoreSentinel.value
  if (!el) return
  loadMoreObserver = new IntersectionObserver(
    entries => {
      const visible = entries.some(e => e.isIntersecting)
      if (!visible) {
        // 滚出视口后恢复额度，允许再次下滑加载
        refillAutoChainBudget()
        return
      }
      loadMore(false)
    },
    { root: null, rootMargin: '240px 0px', threshold: 0 }
  )
  loadMoreObserver.observe(el)
}

function reload() {
  clearSelection()
  loadPhotos(true)
}

function init() {
  loading.value = true
  Promise.all([loadAlbum(), loadPhotos(true)]).finally(() => {
    loading.value = false
  })
}

function onKeydown(e) {
  if (e.key === 'Escape') {
    if (drawOpen.value) return
    if (detailOpen.value) {
      closePhotoDetail()
      return
    }
    if (ctxMenu.visible) {
      closeCtxMenu()
      return
    }
    if (mediaVisible.value) closeMedia()
  }
  if (!mediaVisible.value) return
  if (e.key === 'ArrowLeft') shiftMedia(-1)
  if (e.key === 'ArrowRight') shiftMedia(1)
}

function onWindowBlurOrScroll() {
  closeCtxMenu()
  scheduleVirtualUpdate()
}

watch(() => route.params.albumId, (id) => {
  if (id) init()
})

watch(
  () => photoScoreStore.finishedAt,
  (at) => {
    if (!at) return
    const p = photoScoreStore.progress
    if (String(p.albumId) !== String(albumId.value)) return
    if (Number(p.status) !== 1) return
    reload()
  }
)

watch(currentMedia, (item) => {
  if (detailOpen.value && item) {
    detailPhoto.value = item
  }
  if (mediaVisible.value && item?.fileType === 2) {
    reloadVideoProxy()
  }
})

watch(detailOpen, () => {
  // 抽屉开合后重绘，让图片适配收缩后的展示区
  nextTick(() => paintImageTransform(true))
})

watch(
  [hasMore, () => photoList.value.length, loading],
  async () => {
    await nextTick()
    if (hasMore.value && photoList.value.length && !loading.value) {
      setupLoadMoreObserver()
    } else {
      teardownLoadMoreObserver()
    }
  }
)

onMounted(() => {
  window.addEventListener('keydown', onKeydown)
  window.addEventListener('scroll', onWindowBlurOrScroll, true)
  window.addEventListener('resize', onWindowBlurOrScroll)
  window.addEventListener('blur', onWindowBlurOrScroll)
  nextTick(() => {
    scheduleVirtualUpdate()
    setupGridResizeObserver()
  })
})
onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown)
  window.removeEventListener('scroll', onWindowBlurOrScroll, true)
  window.removeEventListener('resize', onWindowBlurOrScroll)
  window.removeEventListener('blur', onWindowBlurOrScroll)
  teardownLoadMoreObserver()
  teardownGridResizeObserver()
  if (virtualRaf) cancelAnimationFrame(virtualRaf)
  if (clickTimer.value) clearTimeout(clickTimer.value)
  if (imagePaintRaf) cancelAnimationFrame(imagePaintRaf)
  if (wheelZoomRaf) cancelAnimationFrame(wheelZoomRaf)
  if (zoomLabelRaf) cancelAnimationFrame(zoomLabelRaf)
  closeCtxMenu()
})

init()
</script>

<style scoped lang="scss">
.album-detail {
  --muted: #999;
  --select-blue: #4c8dff;
  min-height: calc(100vh - 84px);
  margin: -20px;
  padding: 20px 28px 88px;
  background: #fff;
}

.detail-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.header-left {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  min-width: 0;
}

.back-btn {
  border: none;
  background: transparent;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: #333;
  flex-shrink: 0;
  margin-top: 2px;

  &:hover {
    background: #f3f3f3;
  }
}

.header-info {
  min-width: 0;
}

.album-title {
  margin: 0;
  font-size: 24px;
  font-weight: 700;
  line-height: 1.3;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.album-desc {
  margin-top: 6px;
  font-size: 13px;
  color: #666;
  cursor: pointer;
  min-height: 24px;

  &.placeholder {
    color: var(--muted);
  }
}

.header-right {
  flex-shrink: 0;
}

.hidden-input {
  display: none;
}

.mr4 {
  margin-right: 4px;
}

.detail-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  gap: 12px;
}

.toolbar-left,
.toolbar-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.select-btn {
  width: 28px;
  height: 28px;
  border: 1.5px solid #d0d0d0;
  border-radius: 50%;
  background: #fff;
  color: #8a8a8a;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: background 0.2s ease, border-color 0.2s ease, color 0.2s ease, transform 0.15s ease;

  &:hover {
    border-color: var(--select-blue);
    color: var(--select-blue);
  }

  &.active {
    background: var(--select-blue);
    border-color: var(--select-blue);
    color: #fff;
  }

  &:active {
    transform: scale(0.92);
  }
}

.item-count,
.selected-count,
.mode-tip {
  font-size: 13px;
  color: var(--muted);
}

.selected-count {
  color: #333;
  font-weight: 500;
}

.mode-tip {
  color: #637dff;
}

.tool-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: none;
  background: transparent;
  color: #555;
  font-size: 13px;
  cursor: pointer;
  padding: 4px 0;

  &:hover {
    color: #111;
  }

  &.icon-only {
    padding: 4px;
  }
}

.photo-grid-wrap {
  position: relative;
  width: 100%;
}

.photo-grid-phantom {
  width: 100%;
  pointer-events: none;
}

.photo-grid {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  display: grid;
  will-change: transform;
}

.photo-cell {
  position: relative;
  aspect-ratio: 1;
  border-radius: 2px;
  background: transparent;
  cursor: pointer;
  user-select: none;
  -webkit-user-drag: none;
  box-sizing: border-box;

  .photo-inner {
    position: absolute;
    inset: 0;
    border-radius: 2px;
    overflow: hidden;
    background: #f2f2f2;
  }

  img,
  .video-thumb,
  .thumb-placeholder {
    width: 100%;
    height: 100%;
    object-fit: cover;
    display: block;
    background: #1a1a1a;
    pointer-events: none;
  }

  .thumb-placeholder {
    background: #e8e8e8;
  }

  &:hover:not(.selected) .photo-inner {
    box-shadow: 0 2px 10px rgba(0, 0, 0, 0.08);
  }

  &:hover .check-mark {
    opacity: 1;
    transform: scale(1);
  }

  /* 选中：蓝色外框 + 白色内边距（参考阿里云盘） */
  &.selected {
    background: #fff;
    box-shadow: inset 0 0 0 2px var(--select-blue);
  }

  &.selected .photo-inner {
    inset: 4px;
    border-radius: 1px;
    box-shadow: none;
  }

  &.selected .check-mark {
    opacity: 1;
    transform: scale(1);
    background: var(--select-blue);
    color: #fff;
    border-color: var(--select-blue);
  }
}

.photo-grid.is-selecting .photo-cell .check-mark {
  opacity: 0.55;
}

.video-mark {
  position: absolute;
  left: 6px;
  bottom: 6px;
  min-height: 20px;
  padding: 0 6px;
  border-radius: 10px;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 3px;
  z-index: 1;
  font-size: 11px;
  line-height: 1;
  font-variant-numeric: tabular-nums;
}

.video-duration {
  transform: translateY(0.5px);
}

.score-mark {
  position: absolute;
  right: 6px;
  bottom: 6px;
  min-width: 22px;
  height: 20px;
  padding: 0 5px;
  border-radius: 10px;
  font-size: 11px;
  line-height: 20px;
  text-align: center;
  font-variant-numeric: tabular-nums;
  z-index: 1;
  color: #fff;
}

.score-mark.pass {
  background: rgba(16, 140, 72, 0.82);
}

.score-mark.fail {
  background: rgba(0, 0, 0, 0.5);
}

.ai-mark {
  position: absolute;
  top: 6px;
  right: 6px;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.04em;
  z-index: 1;
  color: #fff;
  background: rgba(88, 64, 200, 0.88);
}

.draw-tip {
  margin: 0 0 16px;
  font-size: 13px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
}

.check-mark {
  position: absolute;
  top: 6px;
  left: 6px;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  border: 1.5px solid rgba(255, 255, 255, 0.95);
  background: rgba(0, 0, 0, 0.18);
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transform: scale(0.72);
  transition: opacity 0.18s ease, transform 0.22s cubic-bezier(0.22, 1, 0.36, 1),
    background 0.18s ease, border-color 0.18s ease;
  z-index: 2;
  backdrop-filter: blur(2px);
}

.photo-cell.selected .check-mark {
  top: 8px;
  left: 8px;
}

.empty-box {
  padding: 80px 0;
}

.detail-footer {
  margin-top: 28px;
  text-align: center;
  font-size: 13px;
  color: var(--muted);
}

.load-more-sentinel {
  min-height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--el-text-color-secondary);
  cursor: pointer;
  user-select: none;
}

.load-more-sentinel:hover {
  color: var(--el-color-primary);
}

.selection-bar {
  position: fixed;
  left: 50%;
  bottom: 28px;
  transform: translateX(-50%);
  z-index: 2500;
  display: flex;
  align-items: center;
  gap: 2px;
  padding: 8px 12px;
  border-radius: 28px;
  background: rgba(45, 45, 45, 0.92);
  box-shadow: 0 8px 28px rgba(0, 0, 0, 0.22);
  backdrop-filter: blur(8px);
}

.sel-btn {
  width: 42px;
  height: 42px;
  border: none;
  border-radius: 50%;
  background: transparent;
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: background 0.15s ease;

  &:hover {
    background: rgba(255, 255, 255, 0.14);
  }
}

.sel-bar-enter-active,
.sel-bar-leave-active {
  transition: opacity 0.18s ease, transform 0.18s ease;
}

.sel-bar-enter-from,
.sel-bar-leave-to {
  opacity: 0;
  transform: translateX(-50%) translateY(12px);
}

.media-viewer {
  position: fixed;
  inset: 0;
  z-index: 3000;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  user-select: none;
  -webkit-user-select: none;
}

.media-stage {
  position: absolute;
  inset: 0;
  z-index: 1;
  transition: right 0.22s ease;
}

.media-viewer.detail-open .media-stage {
  right: 360px;
}

.media-close,
.media-nav {
  position: absolute;
  width: 40px;
  height: 40px;
  border: none;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.06);
  color: #333;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  z-index: 2;
  transition: background 0.15s ease;

  &:hover {
    background: rgba(0, 0, 0, 0.12);
  }
}

.media-close {
  position: fixed;
  top: 20px;
  left: 20px;
  z-index: 3020;
}

.media-nav.prev {
  left: 24px;
  top: 50%;
  transform: translateY(-50%);
}

.media-nav.next {
  right: 24px;
  top: 50%;
  transform: translateY(-50%);
}

.media-actions {
  position: fixed;
  top: 18px;
  right: 24px;
  z-index: 3020;
  display: flex;
  align-items: center;
  gap: 6px;
}

.media-action-btn {
  width: 40px;
  height: 40px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: #3a3a3a;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease;

  &:hover,
  &.active {
    background: rgba(0, 0, 0, 0.06);
    color: #111;
  }
}

.media-canvas {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  cursor: default;
  z-index: 1;
  user-select: none;
  -webkit-user-select: none;
  contain: layout style;
}

.media-image-layer {
  display: flex;
  align-items: center;
  justify-content: center;
  transform: translate3d(0, 0, 0);
  transform-origin: center center;
  will-change: transform;
  backface-visibility: hidden;
}

.media-image {
  width: auto;
  height: auto;
  object-fit: contain;
  border-radius: 2px;
  outline: none;
  user-select: none;
  -webkit-user-select: none;
  -webkit-user-drag: none;
  pointer-events: none;
  /* 限制显示尺寸，减轻高倍 scale 卡顿 */
  max-width: min(92vw, 1200px);
  max-height: min(80vh, 800px);

  &.is-original {
    max-width: min(92vw, 2400px);
    max-height: min(86vh, 1600px);
  }
}

.media-video-wrap {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1;
}

.media-video {
  max-width: min(92%, 1400px);
  max-height: min(78%, 78vh);
  width: auto;
  height: auto;
  object-fit: contain;
  border-radius: 2px;
  outline: none;
  user-select: none;
  background: #000;
  transition: max-width 0.22s ease, max-height 0.22s ease;
}

.media-video-toolbar {
  position: absolute;
  left: 50%;
  bottom: 28px;
  transform: translateX(-50%);
  z-index: 3;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 14px;
  border-radius: 22px;
  background: rgba(0, 0, 0, 0.78);
  color: #fff;
}

.media-video-field {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  margin: 0;

  span {
    opacity: 0.85;
  }

  select {
    border: none;
    border-radius: 6px;
    padding: 4px 8px;
    background: rgba(255, 255, 255, 0.14);
    color: #fff;
    outline: none;
    cursor: pointer;

    option {
      color: #111;
    }
  }

  &.is-disabled {
    opacity: 0.45;

    select {
      cursor: not-allowed;
    }
  }
}

.media-toolbar {
  position: absolute;
  left: 50%;
  bottom: 28px;
  transform: translateX(-50%);
  z-index: 3;
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 10px;
  border-radius: 22px;
  background: rgba(0, 0, 0, 0.78);
  color: #fff;
}

.media-tool-btn {
  width: 34px;
  height: 34px;
  border: none;
  border-radius: 50%;
  background: transparent;
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: background 0.15s ease;

  &:hover {
    background: rgba(255, 255, 255, 0.16);
  }
}

.media-zoom-label {
  min-width: 48px;
  text-align: center;
  font-size: 13px;
  line-height: 1;
  user-select: none;
}

@media (max-width: 768px) {
  .album-detail {
    margin: -15px;
    padding: 16px 14px 40px;
  }

  .album-title {
    font-size: 20px;
  }

  .detail-header {
    flex-direction: column;
  }
}
</style>

<style lang="scss">
/* 预览层之上的确认框 */
body > .el-overlay:has(.above-media-msgbox) {
  z-index: 4300 !important;
}

body > .el-overlay.above-media-draw-overlay,
body > .el-overlay:has(.above-media-draw-dialog) {
  z-index: 5100 !important;
}

.el-popper.above-media-draw-select-popper {
  z-index: 5200 !important;
}

.add-to-dialog {
  border-radius: 12px;
  overflow: hidden;

  .el-dialog__header {
    margin: 0;
    padding: 18px 20px 12px;
  }

  .el-dialog__title {
    font-size: 18px;
    font-weight: 600;
    color: #1f1f1f;
  }

  .el-dialog__headerbtn {
    top: 18px;
    right: 18px;
    width: 28px;
    height: 28px;
  }

  .el-dialog__body {
    padding: 4px 12px 8px;
  }

  .el-dialog__footer {
    padding: 10px 16px 16px;
  }
}

.add-to-list {
  max-height: 420px;
  overflow: auto;
  padding: 4px 0;
}

.add-to-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border-radius: 10px;
  cursor: pointer;
  border: 1.5px solid transparent;
  transition: background 0.15s ease, border-color 0.15s ease;

  &:hover {
    background: #f5f5f5;
  }

  &.selected {
    background: #f5f5f5;
  }

  &.is-editing {
    border-color: #4c8dff;
    background: #fff;
    cursor: default;
  }
}

.add-to-cover {
  width: 48px;
  height: 48px;
  border-radius: 8px;
  overflow: hidden;
  flex-shrink: 0;
  background: #ececec;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #9a9a9a;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }

  &.is-placeholder {
    background: #efefef;
  }
}

.add-to-name {
  flex: 1;
  min-width: 0;
  font-size: 15px;
  color: #222;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.add-to-name-input {
  flex: 1;
  min-width: 0;
  height: 34px;
  border: none;
  outline: none;
  background: transparent;
  font-size: 15px;
  color: #1a73e8;
  padding: 0 4px;
}

.add-to-count {
  flex-shrink: 0;
  font-size: 13px;
  color: #8a8a8a;
  min-width: 28px;
  text-align: right;
}

.add-to-icon-btn {
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 50%;
  background: #e8e8e8;
  color: #555;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  flex-shrink: 0;

  &.ok {
    background: #4c8dff;
    color: #fff;
  }

  &:hover {
    filter: brightness(0.96);
  }
}

.add-to-empty {
  padding: 36px 12px;
  text-align: center;
  color: #999;
  font-size: 13px;
}

.add-to-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.add-to-create-link {
  border: none;
  background: transparent;
  color: #1a73e8;
  font-size: 14px;
  cursor: pointer;
  padding: 0;

  &:hover {
    color: #1558b0;
  }
}

.add-to-footer-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.photos-ctx-menu {
  position: fixed;
  z-index: 3200;
  min-width: 200px;
  padding: 6px 0;
  background: #fff;
  border-radius: 10px;
  box-shadow: 0 8px 28px rgba(0, 0, 0, 0.16), 0 0 0 1px rgba(0, 0, 0, 0.04);
  user-select: none;
}

.photos-ctx-menu .ctx-item {
  width: 100%;
  border: none;
  background: transparent;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 16px;
  font-size: 14px;
  color: #222;
  cursor: pointer;
  text-align: left;

  &:hover {
    background: #f3f3f3;
  }

  &.danger {
    color: #e85d5d;
  }

  .el-icon {
    color: #555;
  }
}

.photos-ctx-menu.type-item .ctx-item {
  gap: 0;
}

.photos-ctx-menu .ctx-divider {
  height: 1px;
  margin: 4px 10px;
  background: #ececec;
}

.photo-detail-backdrop {
  position: fixed;
  inset: 0;
  z-index: 3005;
  background: rgba(0, 0, 0, 0.18);
}

.photo-detail-panel {
  position: fixed;
  /* 避开顶部返回/操作按钮区域，只在下方红框范围弹出 */
  top: 64px;
  right: 0;
  bottom: 0;
  width: 360px;
  z-index: 3010;
  background: #fff;
  box-shadow: -4px 0 24px rgba(0, 0, 0, 0.08);
  border-radius: 12px 0 0 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.photo-detail-head {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 14px 12px 18px;
  border-bottom: 1px solid #f0f0f0;
}

.detail-head-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #222;
}

.detail-close-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: #666;
  cursor: pointer;

  &:hover {
    background: #f3f3f3;
    color: #222;
  }
}

.detail-panel-enter-active,
.detail-panel-leave-active {
  transition: transform 0.22s ease, opacity 0.22s ease;
}

.detail-panel-enter-from,
.detail-panel-leave-to {
  transform: translateX(100%);
  opacity: 0.6;
}

.photo-detail-album {
  flex-shrink: 0;
  padding: 16px 18px 14px;
  border-bottom: 1px solid #f0f0f0;
  background: #fff;
}

.photo-detail-info {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 16px 18px 28px;
}

.detail-section-title {
  margin: 0 0 14px;
  font-size: 15px;
  font-weight: 600;
  color: #222;
}

.detail-album-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.album-tag {
  border: none;
  border-radius: 6px;
  padding: 6px 12px;
  font-size: 13px;
  cursor: pointer;
  background: #f0f0f0;
  color: #333;

  &.ghost {
    background: #f5f5f5;
    color: #666;
  }

  &:hover {
    background: #e8e8e8;
  }
}

.photo-detail-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.photo-detail-row {
  display: flex;
  gap: 16px;
  font-size: 13px;
  line-height: 1.5;

  .label {
    width: 84px;
    flex-shrink: 0;
    color: #999;
  }

  .value {
    flex: 1;
    min-width: 0;
    color: #333;
    word-break: break-all;
  }
}
</style>

<style lang="scss">
.upload-progress-mask {
  position: fixed;
  inset: 0;
  z-index: 5000;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}

.upload-progress-panel {
  width: min(440px, 100%);
  background: #fff;
  border-radius: 12px;
  padding: 22px 24px 18px;
  box-shadow: 0 16px 48px rgba(0, 0, 0, 0.18);
}

.upload-progress-title {
  font-size: 17px;
  font-weight: 650;
  color: #1a1a1a;
  margin-bottom: 14px;
}

.upload-progress-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  font-size: 13px;
  color: #666;
}

.upload-progress-current {
  margin-top: 12px;
  font-size: 13px;
  color: #333;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.upload-progress-list {
  list-style: none;
  margin: 14px 0 0;
  padding: 0;
  max-height: 168px;
  overflow: auto;
  border-top: 1px solid #f0f0f0;
}

.upload-progress-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 0;
  font-size: 12px;
  border-bottom: 1px solid #f7f7f7;

  &.is-fail .upload-item-meta {
    color: #f56c6c;
  }

  &.is-done .upload-item-meta {
    color: #67c23a;
  }

  &.is-uploading .upload-item-meta,
  &.is-processing .upload-item-meta {
    color: #4c8dff;
  }
}

.upload-item-name {
  min-width: 0;
  flex: 1;
  color: #444;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.upload-item-meta {
  flex-shrink: 0;
  color: #999;
  font-variant-numeric: tabular-nums;
}

.upload-progress-tip {
  margin-top: 14px;
  font-size: 12px;
  color: #999;
  line-height: 1.4;
}

.upload-fade-enter-active,
.upload-fade-leave-active {
  transition: opacity 0.18s ease;
}

.upload-fade-enter-from,
.upload-fade-leave-to {
  opacity: 0;
}
</style>
