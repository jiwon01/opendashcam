package com.example.openblackbox

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.net.Uri
import android.util.LruCache
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.openblackbox.databinding.ItemRecordingBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RecordingAdapter(
    private val onPlayClick: (RecordingItem) -> Unit,
    private val onShareClick: (RecordingItem) -> Unit,
    private val onDeleteClick: (RecordingItem) -> Unit
) : RecyclerView.Adapter<RecordingAdapter.RecordingViewHolder>() {

    private val items = mutableListOf<RecordingItem>()
    private val adapterScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var appContext: Context? = null

    private val thumbnailCache = object : LruCache<String, Bitmap>(30) {}

    fun submitList(recordings: List<RecordingItem>) {
        items.clear()
        items.addAll(recordings)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecordingViewHolder {
        if (appContext == null) {
            appContext = parent.context.applicationContext
        }
        val binding = ItemRecordingBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return RecordingViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RecordingViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun onViewRecycled(holder: RecordingViewHolder) {
        holder.clearThumbnailRequest()
        super.onViewRecycled(holder)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        adapterScope.cancel()
    }

    override fun getItemCount(): Int = items.size

    private fun loadThumbnail(holder: RecordingViewHolder, item: RecordingItem) {
        val key = "${item.contentUri}_${item.modifiedAtMillis}"
        holder.boundThumbnailKey = key
        holder.thumbnailJob?.cancel()
        holder.binding.imageThumb.setImageResource(R.drawable.bg_thumb_placeholder)

        thumbnailCache.get(key)?.let { cached ->
            holder.binding.imageThumb.setImageBitmap(cached)
            return
        }

        holder.thumbnailJob = adapterScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                createThumbnail(item.contentUri)
            }
            if (holder.boundThumbnailKey != key) return@launch
            if (bitmap != null) {
                thumbnailCache.put(key, bitmap)
                holder.binding.imageThumb.setImageBitmap(bitmap)
            } else {
                holder.binding.imageThumb.setImageResource(R.drawable.bg_thumb_placeholder)
            }
        }
    }

    private fun createThumbnail(uri: Uri): Bitmap? {
        val context = appContext ?: return null
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val frame = retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            frame?.let {
                ThumbnailUtils.extractThumbnail(it, 640, 360)
            }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    inner class RecordingViewHolder(
        val binding: ItemRecordingBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        var thumbnailJob: Job? = null
        var boundThumbnailKey: String? = null

        fun bind(item: RecordingItem) {
            val date = StorageRepository.formatDate(item.modifiedAtMillis)
            val size = StorageRepository.formatSize(item.sizeBytes)
            binding.textName.text = item.name
            binding.textInfo.text = itemView.context.getString(R.string.record_item_info, date, size)
            binding.buttonPlay.setOnClickListener { onPlayClick(item) }
            binding.buttonShare.setOnClickListener { onShareClick(item) }
            binding.buttonDelete.setOnClickListener { onDeleteClick(item) }
            binding.imageThumb.setOnClickListener { onPlayClick(item) }
            binding.root.setOnClickListener { onPlayClick(item) }
            loadThumbnail(this, item)
        }

        fun clearThumbnailRequest() {
            thumbnailJob?.cancel()
            thumbnailJob = null
            boundThumbnailKey = null
        }
    }
}
