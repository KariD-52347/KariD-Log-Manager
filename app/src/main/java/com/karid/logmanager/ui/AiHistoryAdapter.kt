package com.karid.logmanager.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.karid.logmanager.R
import com.karid.logmanager.databinding.ItemAiHistoryBinding
import com.karid.logmanager.model.AiHistoryItem
import com.karid.logmanager.model.AiStatus

class AiHistoryAdapter(
    private val items: MutableList<AiHistoryItem>,
    private val onViewAnswer: (AiHistoryItem) -> Unit
) : RecyclerView.Adapter<AiHistoryAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemAiHistoryBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAiHistoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val ctx = holder.binding.root.context
        val status = item.getStatus()

        holder.binding.tvProblem.text = if (item.problem.length > 40)
            item.problem.take(37) + "..." else item.problem

        holder.binding.tvStatus.text = when (status) {
            AiStatus.SENDING  -> ctx.getString(R.string.ai_status_sending)
            AiStatus.ANSWERED -> ctx.getString(R.string.ai_status_answered)
            AiStatus.ERROR    -> ctx.getString(R.string.ai_status_error)
        }

        holder.binding.tvStatus.setTextColor(when (status) {
            AiStatus.SENDING  -> ctx.getColor(R.color.status_waiting)
            AiStatus.ANSWERED -> ctx.getColor(R.color.status_answered)
            AiStatus.ERROR    -> ctx.getColor(R.color.status_error)
        })

        if ((status == AiStatus.ANSWERED || status == AiStatus.ERROR) && item.answer.isNotEmpty()) {
            holder.binding.btnViewAnswer.text = ctx.getString(R.string.ai_view_answer)
            holder.binding.btnViewAnswer.isEnabled = true
            holder.binding.btnViewAnswer.setOnClickListener { onViewAnswer(item) }
        } else {
            holder.binding.btnViewAnswer.text = ctx.getString(R.string.ai_waiting_answer)
            holder.binding.btnViewAnswer.isEnabled = false
        }
    }

    override fun getItemCount() = items.size

    fun updateItems(newItems: List<AiHistoryItem>) {
        items.clear()
        items.addAll(newItems.takeLast(5).reversed())
        notifyDataSetChanged()
    }
}
