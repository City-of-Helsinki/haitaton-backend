package fi.hel.haitaton.hanke.validation

private typealias ValidationError = String

private val ValidationSuccess: ValidationError? = null

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
    fun validate(f: () -> ValidationError?): ValidationResult {
        return ValidationResult().and(f)
    }

    fun validateFalse(condition: Boolean, path: String): ValidationError? =
        if (condition) path else ValidationSuccess

    fun validateFalse(condition: Boolean?, path: String): ValidationError? =
        if (condition == false) ValidationSuccess else path

    fun notNull(value: Any?, path: String): ValidationError? = validateFalse(value == null, path)

    fun notBlank(value: String, path: String): ValidationError? =
        validateFalse(value.isBlank(), path)

    fun notNullOrBlank(value: String?, path: String): ValidationError? =
        validateFalse(value.isNullOrBlank(), path)

    fun <T> notEmpty(value: Collection<T>, path: String): ValidationError? =
        validateFalse(value.isEmpty(), path)

    fun <T> notNullOrEmpty(value: Collection<T>?, path: String): ValidationError? =
        validateFalse(value?.isEmpty(), path)

    /** Check run the validation lambda only if the pre-condition is true. */
    fun given(condition: Boolean, f: () -> ValidationError?): ValidationError? =
        if (condition) f() else ValidationSuccess

    /**
     * Use only the first validation error from the ones supplied. Can be used to e.g. check for
     * null values somewhere along a path.
     */
    fun firstOf(vararg fs: ValidationError?): ValidationError? =
        fs.filterNotNull().firstOrNull() ?: ValidationSuccess
}

class ValidationResult {
    private val errorPaths: MutableList<String> = mutableListOf()

    fun errorPaths(): List<String> = errorPaths
    fun isOk() = errorPaths.isEmpty()

    fun and(f: () -> ValidationError?): ValidationResult {
        f()?.let { error -> this.errorPaths.add(error) }
        return this
    }

    fun <T> andAllIn(
        values: Collection<T>,
        path: String,
        f: (T, String) -> ValidationResult
    ): ValidationResult {
        this.errorPaths.addAll(
            values.flatMapIndexed { index: Int, value: T -> f(value, "$path[$index]").errorPaths() }
        )
        return this
    }
}
