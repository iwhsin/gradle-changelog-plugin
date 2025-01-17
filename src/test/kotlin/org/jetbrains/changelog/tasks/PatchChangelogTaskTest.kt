package org.jetbrains.changelog.tasks

import org.jetbrains.changelog.BaseTest
import org.jetbrains.changelog.ChangelogPluginConstants.PATCH_CHANGELOG_TASK_NAME
import org.jetbrains.changelog.exceptions.MissingVersionException
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PatchChangelogTaskTest : BaseTest() {

    @BeforeTest
    fun localSetUp() {
        changelog =
            """
            # Changelog
            ## [Unreleased]
            ### Added
            - foo
            """

        buildFile =
            """
            plugins {
                id 'org.jetbrains.changelog'
            }
            changelog {
                version = "1.0.0"
            }
            """
    }

    @Test
    fun `patches Unreleased version to the current one and creates empty Unreleased above`() {
        project.evaluate()
        runTask(PATCH_CHANGELOG_TASK_NAME)

        assertEquals(
            """
            ### Added
            - foo
            """.trimIndent(),
            extension.get(version).toText()
        )

        assertEquals(
            """
            ## [Unreleased]
            ### Added
            
            ### Changed
            
            ### Deprecated
            
            ### Removed
            
            ### Fixed
            
            ### Security
            """.trimIndent(),
            extension.getUnreleased().withHeader(true).toText()
        )
    }

    @Test
    fun `patches Unreleased version to the current one`() {
        buildFile =
            """
            plugins {
                id 'org.jetbrains.changelog'
            }
            changelog {
                version = "1.0.0"
                keepUnreleasedSection = false
            }
            """

        project.evaluate()
        runTask(PATCH_CHANGELOG_TASK_NAME)

        assertEquals(
            """
            ### Added
            - foo
            """.trimIndent(),
            extension.get(version).toText()
        )

        assertFailsWith<MissingVersionException> {
            extension.getUnreleased()
        }
    }

    @Test
    fun `applies custom header patcher`() {
        buildFile =
            """
            plugins {
                id 'org.jetbrains.changelog'
            }
            changelog {
                version = "1.0.0"
                header = provider { "Foo ${'$'}{version.get()} bar" }
            }
            """

        project.evaluate()
        runTask(PATCH_CHANGELOG_TASK_NAME)

        assertEquals("## Foo 1.0.0 bar", extension.get(version).header)
    }

    @Test
    fun `applies custom header with date`() {
        changelog =
            """
            # Changelog
            All notable changes to this project will be documented in this file.

            ## [Unreleased]
            ### Added
            - Some other thing added.

            ## [1.0.0] - 2020-07-02

            ### Added
            - Something added.
            """
        buildFile =
            """
            import java.text.SimpleDateFormat
            import java.util.Date

            plugins {
                id 'org.jetbrains.changelog'
            }
            changelog {
                version = "1.0.0"
                header = provider {
                    "[${'$'}{version.get()}] - ${'$'}{new SimpleDateFormat("yyyy-MM-dd").format(new Date())}"
                }
            }
            """

        project.evaluate()
        runTask(PATCH_CHANGELOG_TASK_NAME)

        val date = SimpleDateFormat("yyyy-MM-dd").format(Date())
        assertEquals("## [1.0.0] - $date", extension.get(version).header)
    }

    @Test
    fun `doesn't patch changelog if no change notes provided in Unreleased section`() {
        changelog =
            """
            # Changelog
            ## [Unreleased]
            """
        buildFile =
            """
            plugins {
                id 'org.jetbrains.changelog'
            }
            changelog {
                version = "1.0.0"
                patchEmpty = false
            }
            """

        project.evaluate()

        val result = runFailingTask(PATCH_CHANGELOG_TASK_NAME)

        assertFailsWith<MissingVersionException> {
            extension.get(version)
        }

        assertTrue(result.output.contains(":$PATCH_CHANGELOG_TASK_NAME task requires release note to be provided."))
    }

    @Test
    fun `create empty groups for the new Unreleased section`() {
        project.evaluate()
        runTask(PATCH_CHANGELOG_TASK_NAME)

        assertEquals(
            """
            ## [Unreleased]
            ### Added
            
            ### Changed
            
            ### Deprecated
            
            ### Removed
            
            ### Fixed
            
            ### Security
            """.trimIndent(),
            extension.getUnreleased().withHeader(true).toText()
        )
    }

    @Test
    fun `remove empty groups for the new released section`() {
        changelog =
            """
            # Changelog
            ## [Unreleased]
            ### Added
            - foo
            
            ### Changed
            
            ### Deprecated
            
            ### Removed
            - bar
            
            ### Fixed
            
            ### Security
            """

        project.evaluate()
        runTask(PATCH_CHANGELOG_TASK_NAME)

        assertEquals(
            """
            ## [1.0.0]
            ### Added
            - foo
            
            ### Removed
            - bar
            """.trimIndent(),
            extension.get(version).withHeader(true).toText()
        )
    }

    @Test
    fun `create empty custom groups for the new Unreleased section`() {
        buildFile =
            """
            plugins {
                id 'org.jetbrains.changelog'
            }
            changelog {
                version = "1.0.0"
                groups = ["Aaaa", "Bbb"]
            }
            """

        project.evaluate()
        runTask(PATCH_CHANGELOG_TASK_NAME)

        assertEquals(
            """
            ## [Unreleased]
            ### Aaaa

            ### Bbb
            """.trimIndent(),
            extension.getUnreleased().withHeader(true).toText()
        )
    }

    @Test
    fun `don't create groups for the new Unreleased section if empty array is provided`() {
        buildFile =
            """
            plugins {
                id 'org.jetbrains.changelog'
            }
            changelog {
                version = "1.0.0"
                groups = []
            }
            """

        project.evaluate()
        runTask(PATCH_CHANGELOG_TASK_NAME)

        assertEquals(
            """
            ## [Unreleased]
            
            """.trimIndent(),
            extension.getUnreleased().withHeader(true).toText()
        )
    }

    @Test
    fun `throws MissingReleaseNoteException when Unreleased section is not present`() {
        val unreleasedTerm = "Not released"
        buildFile =
            """
            plugins {
                id 'org.jetbrains.changelog'
            }
            changelog {
                version = "1.0.0"
                unreleasedTerm = "$unreleasedTerm"
            }
            """
        changelog =
            """
            ## [1.0.0]
            """

        project.evaluate()
        runTask(PATCH_CHANGELOG_TASK_NAME, "--warn")

        assertFailsWith<MissingVersionException> {
            extension.getUnreleased()
        }
    }

    @Test
    fun `throws VersionNotSpecifiedException when changelog extension has no version provided`() {
        buildFile =
            """
            plugins {
                id 'org.jetbrains.changelog'
            }
            changelog {
            }
            """

        project.evaluate()

        val result = runFailingTask(PATCH_CHANGELOG_TASK_NAME)

        assertTrue(
            result.output.contains(
                "org.jetbrains.changelog.exceptions.VersionNotSpecifiedException: Version is missing. " +
                    "Please provide the project version to the `project` or `changelog.version` property explicitly."
            )
        )
    }

    @Test
    fun `patch changelog with a custom release note`() {
        buildFile =
            """
            plugins {
                id 'org.jetbrains.changelog'
            }
            changelog {
                version = "1.0.0"
                groups = []
            }
            """
        changelog =
            """
            # My Changelog
            Foo bar buz.
            
            ## [Unreleased]
            - Foo
            - Bar
            
            ## [0.1.0]
            ### Added
            - Buz
            """
        project.evaluate()

        runTask(PATCH_CHANGELOG_TASK_NAME, "--release-note=- asd")

        assertEquals(
            """
            - asd
            """.trimIndent(),
            extension.get("1.0.0").toText()
        )
        assertEquals(
            """
            # My Changelog
            Foo bar buz.
            
            ## [Unreleased]
            
            ## [1.0.0]
            - asd

            ## [0.1.0]
            ### Added
            - Buz
            """.trimIndent(),
            File(extension.path.get()).readText()
        )
    }

    @Test
    fun `task loads from the configuration cache`() {
        runTask(PATCH_CHANGELOG_TASK_NAME, "--configuration-cache")
        val result = runTask(PATCH_CHANGELOG_TASK_NAME, "--configuration-cache")

        assertTrue(result.output.contains("Reusing configuration cache."))
    }
}
