package com.example.myapplication

class DayAdapter(var days: MutableList<Int> = mutableListOf()) {

    init {
        if (days.isEmpty()){
            days.addAll((1..31).toMutableList())
        }
    }

    /** 최대 인덱스 반환 */
    fun getMaxIndex(): Int = getSize() - 1

    /** 최소 인덱스 반환 */
    fun getMinIndex(): Int = 0

    fun getPosition(vale: String): Int = days.indexOf(vale.toInt()).clamp(getMinIndex(), getMaxIndex())

    fun getTextWithMaximumLength(): String = days.maxOrNull().toString()

    fun getValue(position: Int): String {
        if (position >= getMinIndex() && position <= getMaxIndex())
            return days[position].toString()

        if (position <= getMaxIndex())
            return days[position + getSize()].toString()

        if (position >= getMinIndex())
            return days[position - getSize()].toString()

        return ""
    }

    fun getSize(): Int = days.size
}