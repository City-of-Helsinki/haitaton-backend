package fi.hel.haitaton.hanke.validation

import java.time.ZonedDateTime

/**
 * Validator helper methods for on-the-fly validation (not at the REST API-boundary or the database
 * boundary).
 *
 * Keeps track of the paths of validation failures, but not the errors that occurred.
 *
 * @see HankePublicValidator for examples on usage.
 */
object Validators {

    /**
     * Starts a validation chain. Further validations can be chained with [ValidationResult.and].
     */
    fun validate(f: () -> ValidationResult): ValidationResult {
        return ValidationResult.success().and(f)
    }

    /**
     * Starts a validation chain with a success. Useful if only [ValidationResult.andAllIn],
     * [ValidationResult.whenNotNull] or similar are needed.
     */
    fun validate(): ValidationResult = ValidationResult.success()

    fun validateTrue(condition: Boolean?, path: String): ValidationResult =
        if (condition == true) ValidationResult.success() else ValidationResult.failure(path)

    fun validateFalse(condition: Boolean, path: String): ValidationResult =
        if (!condition) ValidationResult.success() else ValidationResult.failure(path)

    fun validateFalse(condition: Boolean?, path: String): ValidationResult =
        if (condition == false) ValidationResult.success() else ValidationResult.failure(path)

    fun notNull(value: Any?, path: String): ValidationResult = validateFalse(value == null, path)

    fun notBlank(value: String, path: String): ValidationResult =
        validateTrue(value.isNotBlank(), path)

    /**
     * Fails if the string is defined, non-empty and has nothing but whitespace characters.
     * - null -> Ok
     * - "" -> Ok
     * - " " -> Fail
     * - "*" -> Ok
     * - "\t\n \t\n" -> Fail
     * - " f " -> Ok
     */
    fun notJustWhitespace(value: String?, path: String): ValidationResult =
        given(!value.isNullOrEmpty()) { notBlank(value!!, path) }

    fun notNullOrBlank(value: String?, path: String): ValidationResult =
        validateFalse(value.isNullOrBlank(), path)

    fun String.notLongerThan(maxLength: Int, path: String): ValidationResult =
        validateFalse(this.length > maxLength, path)

    fun <T> notEmpty(value: Collection<T>, path: String): ValidationResult =
        validateFalse(value.isEmpty(), path)

    fun <T> notNullOrEmpty(value: Collection<T>?, path: String): ValidationResult =
        validateFalse(value?.isEmpty(), path)

    fun isBeforeOrEqual(start: ZonedDateTime, end: ZonedDateTime, path: String): ValidationResult =
        validateFalse(start.isAfter(end), path)

    /**
     * Run the validation only if the pre-condition is true.
     *
     * If needed outside this class, use [ValidationResult.andWhen] instead.
     */
    fun given(condition: Boolean, f: () -> ValidationResult): ValidationResult =
        if (condition) f() else ValidationResult.success()

    /**
     * Use only the first validation error from the ones supplied. Can be used to e.g. check for
     * null values somewhere along a path.
     */
    fun firstOf(vararg fs: ValidationResult): ValidationResult =
        fs.toList().firstOrNull { !it.isOk() } ?: ValidationResult.success()
}

sealed class ValidationResult {
    abstract val errorPaths: List<String>

    /** Using a data class here to avoid the need to implement equals and hashCode for the class. */
    private data class Result(override val errorPaths: List<String> = listOf()) :
        ValidationResult()

    fun errorPaths(): List<String> = errorPaths

    fun isOk() = errorPaths.isEmpty()

    private fun addAll(errorPaths: List<String>): ValidationResult =
        Result(this.errorPaths + errorPaths)

    fun and(f: () -> ValidationResult): ValidationResult {
        return this.addAll(f().errorPaths())
    }

    /**
     * If either this or the validated function is ok, return ok.
     *
     * If both have errors, return all errors.
     */
    fun or(f: () -> ValidationResult): ValidationResult {
        if (isOk()) {
            return empty
        }

        val other = f()
        if (other.isOk()) {
            return empty
        }

        return this.addAll(other.errorPaths())
    }

    /** Run the validation only when the value is not null. */
    fun <T> whenNotNull(value: T?, f: (T) -> ValidationResult): ValidationResult =
        if (value != null) this.and { f(value) } else this

    /** Fail if the value is null. Run the validation when it is not. */
    fun <T> andWithNotNull(
        value: T?,
        path: String,
        f: (T).(String) -> ValidationResult
    ): ValidationResult = if (value != null) this.and { value.f(path) } else and { failure(path) }

    fun <T> andNotNull(
        value: T?,
        path: String,
        f: (T, String) -> ValidationResult
    ): ValidationResult = if (value != null) this.and { f(value, path) } else and { failure(path) }

    /** Check run the validation lambda only if the pre-condition is true. */
    fun andWhen(condition: Boolean, f: () -> ValidationResult): ValidationResult =
        if (condition) this.and(f) else this

    fun <T> andAllIn(
        values: Collection<T>,
        path: String,
        f: (T, String) -> ValidationResult,
    ): ValidationResult {
        return this.addAll(
            values.flatMapIndexed { index: Int, value: T -> f(value, "$path[$index]").errorPaths() }
        )
    }

    companion object {
        private val empty = Result()

        fun success(): ValidationResult = empty

        fun failure(errorPath: String): ValidationResult = Result(listOf(errorPath))

        fun <T> whenNotNull(value: T?, f: (T) -> ValidationResult): ValidationResult =
            success().whenNotNull(value, f)

        fun <T> allIn(
            values: Collection<T>,
            path: String,
            f: (T, String) -> ValidationResult,
        ): ValidationResult = success().andAllIn(values, path, f)
    }
}
