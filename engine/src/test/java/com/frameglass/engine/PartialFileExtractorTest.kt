package com.frameglass.engine

import androidx.media3.common.ParserException
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.mkv.MatroskaExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.EOFException
import java.lang.reflect.Proxy

class PartialFileExtractorTest {
    /** What one read of the wrapped extractor does: a result, a seek target, or a throw. */
    private sealed interface Step {
        data class Result(val code: Int) : Step
        data class Seek(val position: Long) : Step
        data class Fail(val error: Exception) : Step
    }

    private class Scripted(private val steps: ArrayDeque<Step>) : Extractor {
        var released = false

        override fun sniff(input: ExtractorInput) = true
        override fun init(output: ExtractorOutput) = Unit
        override fun seek(position: Long, timeUs: Long) = Unit
        override fun release() {
            released = true
        }

        override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int =
            when (val step = steps.removeFirst()) {
                is Step.Result -> step.code
                is Step.Fail -> throw step.error
                is Step.Seek -> {
                    seekPosition.position = step.position
                    Extractor.RESULT_SEEK
                }
            }
    }

    private val flags = mutableListOf<Int>()
    private val made = mutableListOf<Scripted>()

    private fun extractor(vararg scripts: List<Step>): PartialFileExtractor {
        val left = ArrayDeque(scripts.toList())
        return PartialFileExtractor { flag ->
            flags += flag
            Scripted(ArrayDeque(left.removeFirst())).also { made += it }
        }.also { it.init(ExtractorOutput.PLACEHOLDER) }
    }

    private fun input(position: Long, length: Long = FILE_LENGTH): ExtractorInput =
        Proxy.newProxyInstance(ExtractorInput::class.java.classLoader, arrayOf(ExtractorInput::class.java)) { _, method, _ ->
            when (method.name) {
                "getPosition" -> position
                "getLength" -> length
                else -> error("unexpected ${method.name}")
            }
        } as ExtractorInput

    private fun PartialFileExtractor.step(position: Long = CLUSTER, length: Long = FILE_LENGTH): Pair<Int, Long> {
        val holder = PositionHolder()
        val result = read(input(position, length), holder)
        return result to holder.position
    }

    private val more = Step.Result(Extractor.RESULT_CONTINUE)
    private val end = Step.Result(Extractor.RESULT_END_OF_INPUT)
    private val toCues = Step.Seek(CUES_INSIDE)

    @Test
    fun aCompleteFileIsLeftAlone() {
        val partial = extractor(listOf(more, toCues, more, Step.Seek(CLUSTER), more, end))
        assertEquals(Extractor.RESULT_CONTINUE, partial.step().first)
        assertEquals(Extractor.RESULT_SEEK to CUES_INSIDE, partial.step())
        assertEquals(Extractor.RESULT_CONTINUE, partial.step(CUES_INSIDE).first)
        assertEquals(Extractor.RESULT_SEEK to CLUSTER, partial.step(CUES_INSIDE))
        assertEquals(Extractor.RESULT_CONTINUE, partial.step().first)
        assertEquals(Extractor.RESULT_END_OF_INPUT, partial.step().first)
        assertEquals(listOf(0), flags)
    }

    @Test
    fun cuesPastTheEndStartOverWithoutCues() {
        val partial = extractor(listOf(Step.Seek(FILE_LENGTH + 5)), listOf(more))
        assertEquals(Extractor.RESULT_SEEK to 0L, partial.step())
        assertEquals(listOf(0, MatroskaExtractor.FLAG_DISABLE_SEEK_FOR_CUES), flags)
        assertEquals(true, made[0].released)
        assertEquals(Extractor.RESULT_CONTINUE, partial.step(0).first)
    }

    @Test
    fun cuesExactlyAtTheEndCountAsMissing() {
        val partial = extractor(listOf(Step.Seek(FILE_LENGTH)), listOf(more))
        assertEquals(Extractor.RESULT_SEEK to 0L, partial.step())
    }

    @Test
    fun aFileOfUnknownLengthIsLeftToTheDataSource() {
        val partial = extractor(listOf(Step.Seek(FILE_LENGTH + 5)))
        assertEquals(Extractor.RESULT_SEEK to FILE_LENGTH + 5, partial.step(length = -1))
    }

    @Test
    fun cuesCutShortStartOverToo() {
        for (failure in listOf(Step.Fail(EOFException()), end, Step.Fail(IllegalStateException("zero")))) {
            val partial = extractor(listOf(toCues, failure), listOf(more))
            partial.step()
            assertEquals(Extractor.RESULT_SEEK to 0L, partial.step(CUES_INSIDE))
        }
    }

    @Test
    fun malformedCuesStartOverButMalformedClustersDoNot() {
        val error = ParserException.createForMalformedContainer("bad", null)
        val atCues = extractor(listOf(toCues, Step.Fail(error)), listOf(more))
        atCues.step()
        assertEquals(Extractor.RESULT_SEEK to 0L, atCues.step(CUES_INSIDE))

        val atCluster = extractor(listOf(Step.Fail(error)))
        assertSame(error, assertThrows(ParserException::class.java) { atCluster.step() })
    }

    @Test
    fun theEndOfTheDataIsAnErrorOnceCuesAreGone() {
        val partial = extractor(listOf(Step.Seek(FILE_LENGTH + 5)), listOf(more, end))
        partial.step()
        partial.step(0)
        assertThrows(TruncatedFileException::class.java) { partial.step() }
    }

    @Test
    fun aBlockCutInHalfIsAnErrorWithCues() {
        val cut = EOFException()
        val partial = extractor(listOf(more, Step.Fail(cut)))
        partial.step()
        val thrown = assertThrows(TruncatedFileException::class.java) { partial.step() }
        assertSame(cut, thrown.cause)
    }

    @Test
    fun zeroesWhereAClusterShouldBeAreAnError() {
        val partial = extractor(listOf(Step.Fail(IllegalStateException("zero"))))
        assertThrows(TruncatedFileException::class.java) { partial.step() }
    }

    @Test
    fun itStartsOverOnlyOnce() {
        val partial = extractor(listOf(Step.Seek(FILE_LENGTH + 5)), listOf(Step.Seek(FILE_LENGTH + 5)))
        partial.step()
        assertThrows(TruncatedFileException::class.java) { partial.step(0) }
        assertEquals(2, flags.size)
    }

    @Test
    fun otherReadErrorsPassThrough() {
        val unplugged = java.io.IOException("drive gone")
        val partial = extractor(listOf(Step.Fail(unplugged)))
        assertSame(unplugged, assertThrows(java.io.IOException::class.java) { partial.step() })
    }

    private companion object {
        const val CLUSTER = 765L
        const val CUES_INSIDE = 9_000_000L
        const val FILE_LENGTH = 9_566_301L
    }
}
