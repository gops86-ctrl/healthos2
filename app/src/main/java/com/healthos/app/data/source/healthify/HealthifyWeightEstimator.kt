package com.healthos.app.data.source.healthify

import kotlin.math.round

/**
 * Personalized empirical model built from the user's historical HealthifyMe Smart Scale screenshots.
 * These values are estimates, not HealthifyMe's proprietary BIA algorithm.
 */
data class EstimatedBodyComposition(
    val bodyFatPercent: Double,
    val fatMassKg: Double,
    val leanBodyMassKg: Double,
    val muscleMassPercent: Double,
    val muscleMassKg: Double,
    val bmrCalories: Int,
    val hydrationPercent: Double,
    val proteinPercent: Double,
    val skeletalMusclePercent: Double,
    val subcutaneousFatPercent: Double,
    val visceralFatLevel: Int,
    val boneMassPercent: Double,
    val metabolicAge: Int
)

object HealthifyWeightEstimator {
    private const val MIN_CALIBRATED_WEIGHT = 66.6
    private const val MAX_CALIBRATED_WEIGHT = 81.0

    fun estimate(weightKg: Double): EstimatedBodyComposition {
        val w = weightKg.coerceIn(MIN_CALIBRATED_WEIGHT, MAX_CALIBRATED_WEIGHT)

        // Quadratic fit to the user's Smart Scale body-fat readings.
        val bodyFat = (-0.00712401345 * w * w) + (1.59561645 * w) - 58.07261
        val bf = bodyFat.coerceIn(5.0, 45.0)
        val fatMass = w * bf / 100.0
        val leanMass = w - fatMass

        // Katch-McArdle closely reproduces the BMR displayed by HealthifyMe for the supplied readings.
        val bmr = round(370.0 + 21.6 * leanMass).toInt()

        val muscleKg = (0.3729289 * w + 27.93817764).coerceAtMost(leanMass)
        val musclePercent = muscleKg / w * 100.0

        val hydration = -0.39020017 * w + 86.05064199
        val protein = -0.12552135 * w + 27.32330949
        val skeletal = -0.35393518 * w + 77.37589194
        val subcutaneous = 0.46423512 * w - 15.70995625

        val visceral = when {
            w >= 79.0 -> 8
            w >= 75.5 -> 7
            w >= 72.5 -> 6
            w >= 69.0 -> 5
            else -> 4
        }

        val bone = (0.01967453 * w + 1.45724889).coerceIn(2.7, 3.2)
        val metabolicAge = when {
            w >= 78.0 -> 21
            w >= 75.0 -> 20
            w >= 73.0 -> 19
            else -> 18
        }

        return EstimatedBodyComposition(
            bodyFatPercent = bf.round2(),
            fatMassKg = fatMass.round2(),
            leanBodyMassKg = leanMass.round2(),
            muscleMassPercent = musclePercent.round2(),
            muscleMassKg = muscleKg.round2(),
            bmrCalories = bmr,
            hydrationPercent = hydration.round2(),
            proteinPercent = protein.round2(),
            skeletalMusclePercent = skeletal.round2(),
            subcutaneousFatPercent = subcutaneous.round2(),
            visceralFatLevel = visceral,
            boneMassPercent = bone.round2(),
            metabolicAge = metabolicAge
        )
    }

    private fun Double.round2(): Double = round(this * 100.0) / 100.0
}
