package com.guardian.app.ui.customer

data class ValidationResult(
    val valid: Boolean,
    val errors: Map<String, String> = emptyMap()
)

object Validation {

    fun validate(report: CustomerReport): ValidationResult {
        val errors = mutableMapOf<String, String>()

        when (report.category) {
            is Category.Bug -> {
                if (report.description.isBlank()) {
                    errors["description"] = "Please describe the bug"
                }
                if (report.stepsToReproduce.isBlank()) {
                    errors["stepsToReproduce"] = "Steps to reproduce are required"
                }
                if (report.expectedBehavior.isBlank()) {
                    errors["expectedBehavior"] = "Expected behavior is required"
                }
                if (report.actualBehavior.isBlank()) {
                    errors["actualBehavior"] = "Actual behavior is required"
                }
            }
            is Category.Feature -> {
                if (report.description.isBlank()) {
                    errors["description"] = "Please describe the feature request"
                }
            }
            is Category.Suggestion -> {
                if (report.description.isBlank()) {
                    errors["description"] = "Please describe your suggestion"
                }
            }
            is Category.Other -> {
                if (report.description.isBlank()) {
                    errors["description"] = "Please describe your inquiry"
                }
            }
        }

        return ValidationResult(
            valid = errors.isEmpty(),
            errors = errors
        )
    }

    fun isCooldownExpired(lastSubmitTime: Long, cooldownMs: Long = 43_200_000L): Boolean {
        return System.currentTimeMillis() - lastSubmitTime >= cooldownMs
    }

    fun remainingCooldown(lastSubmitTime: Long, cooldownMs: Long = 43_200_000L): Long {
        val elapsed = System.currentTimeMillis() - lastSubmitTime
        return (cooldownMs - elapsed).coerceAtLeast(0L)
    }
}
