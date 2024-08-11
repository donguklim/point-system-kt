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
            itemRanges.add(Triple(multiplier, covered, covered + probWeight - 1))
            covered += probWeight
        }
        weightRanges = itemRanges
        if (weightRanges.isEmpty()) throw IllegalArgumentException("itemRange is empty")
        lastVal = itemRanges.last().third
    }


    private fun selectRandomWithinRange(begin: Int, end: Int, pos: Int): Int {
        if (begin == end) return weightRanges[begin].first

        val mid = begin + (end - begin) / 2
        val midItem = weightRanges[mid]

        if (midItem.second <= pos && pos <= midItem.third) return midItem.first

        if (pos < midItem.second) return selectRandomWithinRange(begin, midItem.second, pos)

        return selectRandomWithinRange(midItem.second, end, pos)
    }
    fun selectRandom(): Int {
        val pos = (0..lastVal).random()

        return selectRandomWithinRange(0,weightRanges.size - 1, pos)
    }
}
