package com.example.point.domain.gamble.utils

fun getGCD(a: Int, b: Int): Int {
    var val1 = a
    var val2 = b

    while (val2 > 0){
        val temp = val1 % val2
        val1 = val2
        val2 = temp
    }

    return val1
}

class RandomSelector(multiplierProbWeights: Map<Int, Int>) {
    private val weightRanges: List<Triple<Int, Int, Int>>
    private val lastVal: Int

    init {
        if (multiplierProbWeights.isEmpty()) throw IllegalArgumentException("empty prob weights is not Allowed")
        var gcdVal = multiplierProbWeights.values.first()
        for ((multiplier, probWeight) in multiplierProbWeights){
            if (probWeight <= 0) continue

            if (multiplier < 0) throw IllegalArgumentException("multiplier must be > 0")
            gcdVal = getGCD(gcdVal, probWeight)
        }
        if (gcdVal > 1){
            for (multiplier in multiplierProbWeights.keys){
                multiplierProbWeights[multiplier]?.let{
                        weight -> weight / gcdVal
                }
            }
        }
        var covered = 0
        val itemRanges: MutableList<Triple<Int, Int, Int>> = mutableListOf()
        for ((multiplier, probWeight) in multiplierProbWeights){
            if (probWeight <= 0) continue

            itemRanges.add(Triple(multiplier, covered, covered + probWeight - 1))
            covered += probWeight
        }
        weightRanges = itemRanges
        if (weightRanges.isEmpty()) throw IllegalArgumentException("itemRange is empty")
        lastVal = itemRanges.last().third
    }

    fun selectRandom(): Int {
        val pos = (0..lastVal).random()

        var begin = 0
        var end = weightRanges.size - 1

        while (begin < end){
            val mid = (end + begin) / 2
            val midItem = weightRanges[mid]

            if (midItem.second <= pos && pos <= midItem.third) return midItem.first

            if (pos < midItem.second) {
                end = mid - 1
            } else {
                begin = mid + 1
            }
        }

        return weightRanges[begin].first
    }
}
