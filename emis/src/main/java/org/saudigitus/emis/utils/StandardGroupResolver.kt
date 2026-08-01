package org.saudigitus.emis.utils

import org.saudigitus.emis.data.model.app_config.Performance

/**
 * Resolves which score-dataElement uids apply to a student in the given [gradeCode], per
 * [Performance.standardGroupMapping].
 *
 * Returns null when no restriction should be applied — either the mapping isn't configured yet,
 * or [gradeCode] doesn't match any configured group. Callers should treat null as "don't narrow
 * further" rather than "no subjects apply", so this stays backward-compatible for programs that
 * haven't configured Standard Groups.
 */
fun Performance.subjectsForGrade(gradeCode: String): Set<String>? {
    val groups = standardGroupMapping?.groups
    if (groups.isNullOrEmpty()) return null

    val group = groups.find { gradeCode in (it.standards ?: emptyList()) } ?: return null

    val universalSubjects = subjects
        ?.filter { it.universal == true }
        ?.map { it.scoreDataElement }
        ?: emptyList()

    return (group.subjects ?: emptyList()).toSet() + universalSubjects
}