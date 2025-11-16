package com.example.medalert

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.medalert.models.Alarm
import com.example.medalert.models.Medication

// The adapter now needs both the list of alarms and the master list of all medications
class AlarmAdapter(
    private val alarms: List<Alarm>,
    private val allMedications: List<Medication>,
    private val onDeleteClick: (Alarm) -> Unit
) : RecyclerView.Adapter<AlarmAdapter.AlarmViewHolder>() {

    class AlarmViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvAlarmTime: TextView = view.findViewById(R.id.tvAlarmTime)
        val tvMedicationNames: TextView = view.findViewById(R.id.tvMedicationNames)
        val tvPillsRemaining: TextView = view.findViewById(R.id.tvPillsRemaining)
        val deleteButton: Button = view.findViewById(R.id.deleteAlarmButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlarmViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_alarm, parent, false)
        return AlarmViewHolder(view)
    }

    override fun onBindViewHolder(holder: AlarmViewHolder, position: Int) {
        val alarm = alarms[position]
        holder.tvAlarmTime.text = alarm.alarmTime

        // Join the names of the medications for this alarm
        holder.tvMedicationNames.text = alarm.medicationNames.joinToString(", ")

        // --- NEW LOOKUP LOGIC ---
        // Find the full Medication objects that correspond to the names in this alarm
        val medsInThisAlarm = allMedications.filter { it.name in alarm.medicationNames }

        // Display the pills remaining for each medication
        val pillsRemainingText = medsInThisAlarm.joinToString("\n") {
            "${it.name}: ${it.pillsRemaining} pills left"
        }
        holder.tvPillsRemaining.text = pillsRemainingText

        holder.deleteButton.setOnClickListener {
            onDeleteClick(alarm)
        }
    }

    override fun getItemCount(): Int = alarms.size
}
