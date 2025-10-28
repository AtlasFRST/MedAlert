package com.example.medalert

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
//import androidx.compose.ui.semantics.text
import androidx.recyclerview.widget.RecyclerView

class AlarmAdapter(
    private val alarms: MutableList<String>,
    private val onDeleteClick: (String) -> Unit
) : RecyclerView.Adapter<AlarmAdapter.AlarmViewHolder>() {

    class AlarmViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val alarmTime: TextView = view.findViewById(R.id.tvAlarmTime)
        val deleteButton: Button = view.findViewById(R.id.btnDeleteAlarm)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlarmViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_alarm, parent, false)
        return AlarmViewHolder(view)
    }

    override fun onBindViewHolder(holder: AlarmViewHolder, position: Int) {
        val alarm = alarms[position]
        holder.alarmTime.text = alarm
        holder.deleteButton.setOnClickListener {
            onDeleteClick(alarm)
        }
    }

    override fun getItemCount() = alarms.size
}
