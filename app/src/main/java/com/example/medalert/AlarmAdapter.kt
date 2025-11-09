package com.example.medalert

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.medalert.models.Alarm

class AlarmAdapter(
    private val alarms: MutableList<Alarm>, // Use the Alarm model
    private val onDeleteClick: (Alarm) -> Unit
) : RecyclerView.Adapter<AlarmAdapter.AlarmViewHolder>() {

    class AlarmViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val alarmTime: TextView = view.findViewById(R.id.tvAlarmTime)
        val medications: TextView = view.findViewById(R.id.tvMedications)
        val deleteButton: Button = view.findViewById(R.id.btnDeleteAlarm)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlarmViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_alarm, parent, false)
        return AlarmViewHolder(view)
    }

    override fun onBindViewHolder(holder: AlarmViewHolder, position: Int) {
        val alarm = alarms[position]
        holder.alarmTime.text = alarm.alarmTime

        // Build the medication list string
        val medText = alarm.medications.joinToString("\n") { med ->
            "• ${med.name} (${med.pillsRemaining} left)"
        }
        holder.medications.text = medText

        holder.deleteButton.setOnClickListener {
            onDeleteClick(alarm)
        }
    }

    override fun getItemCount() = alarms.size
}
