package com.healthos.app.domain.model

data class ExerciseWithSets(
    val exercise: Exercise,
    val sets: List<WorkoutSet>
)

data class StrengthWorkoutDetail(
    val workout: StrengthWorkout,
    val exercises: List<ExerciseWithSets>
)
