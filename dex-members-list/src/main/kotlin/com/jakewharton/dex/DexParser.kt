package com.jakewharton.dex

import com.jakewharton.diffuse.ApiMapping
import com.jakewharton.diffuse.Dex.Companion.toDex
import com.jakewharton.diffuse.DexField
import com.jakewharton.diffuse.DexMember
import com.jakewharton.diffuse.DexMethod
import java.io.File
import java.nio.file.Path

/**
 * Parser for method and field references inside of a `.dex`, `.class`, `.jar`, `.aar`, or `.apk`.
 */
class DexParser private constructor(
  private val bytes: List<ByteArray>,
  private val mapping: ApiMapping = ApiMapping.EMPTY,
  private val desugaring: Desugaring = Desugaring.DISABLED
) {
  /** Configuration for language feature and newer API call desugaring. */
  data class Desugaring(
    /** The minimum API level supported. This affects how much desugaring will occur. */
    val minApiLevel: Int,
    /**
     * The library jar(s) for use in desugaring.
     *
     * Typically this is an `android.jar` from the Android SDK or `rt.jar` from the JDK.
     */
    val libraryJars: List<Path>
  ) {
    companion object {
      @JvmField
      val DISABLED = Desugaring(29, emptyList())
    }
  }

  /**
   * Return a new [DexParser] which uses the supplied [mapping] to translate types and names.
   * These mappings are produced by tools like R8 and ProGuard.
   *
   * @see ApiMapping
   */
  fun withApiMapping(mapping: ApiMapping) = DexParser(bytes, mapping, desugaring)

  /**
   * Return a new [DexParser] which will desugar language features and newer API calls using the
   * supplied [Desugaring] configuration. This is only used for `.jar`, `.aar`, and/or `.class`
   * inputs.
   *
   * @see Desugaring
   */
  fun withDesugaring(desugaring: Desugaring) = DexParser(bytes, mapping, desugaring)

  private val dexMembers by lazy {
    bytes.toDexes(desugaring)
        .map {
          val dex = it.toDex().withMapping(mapping)
          DexMembers(dex.members, dex.declaredMembers, dex.referencedMembers)
        }
        .takeIf { it.isNotEmpty() } // TODO https://youtrack.jetbrains.com/issue/KT-33761
        ?.reduce(DexMembers::plus)
        ?.let {
          DexMembers(
              it.all.toSortedSet().toList(),
              it.declared.toSortedSet().toList(),
              it.referenced.toSortedSet().toList()
          )
        }
        ?: DexMembers(emptyList(), emptyList(), emptyList())
  }

  private class DexMembers(
    val all: List<DexMember>,
    val declared: List<DexMember>,
    val referenced: List<DexMember>
  ) {
    operator fun plus(other: DexMembers): DexMembers {
      return DexMembers(all + other.all, declared + other.declared, referenced + other.referenced)
    }
  }

  fun listMembers(): List<DexMember> = dexMembers.all
  fun listMethods(): List<DexMethod> = listMembers().filterIsInstance<DexMethod>()
  fun listFields(): List<DexField> = listMembers().filterIsInstance<DexField>()

  fun declaredMembers(): List<DexMember> = dexMembers.declared
  fun declaredMethods(): List<DexMethod> = declaredMembers().filterIsInstance<DexMethod>()
  fun declaredFields(): List<DexField> = declaredMembers().filterIsInstance<DexField>()

  fun referencedMembers(): List<DexMember> = dexMembers.referenced
  fun referencedMethods(): List<DexMethod> = referencedMembers().filterIsInstance<DexMethod>()
  fun referencedFields(): List<DexField> = referencedMembers().filterIsInstance<DexField>()

  /** @return the number of dex files parsed. */
  fun dexCount(): Int = bytes.size

  companion object {
    /** Create a [DexParser] from of any `.dex`, `.class`, `.jar`, `.aar`, or `.apk`. */
    @JvmStatic
    @JvmName("fromPath")
    fun Path.toDexParser() = DexParser(listOf(readBytes()))

    /** Create a [DexParser] from of any `.dex`, `.class`, `.jar`, `.aar`, or `.apk`. */
    @JvmStatic
    @JvmName("fromPaths")
    fun Collection<Path>.toDexParser() = DexParser(map { it.readBytes() })

    /** Create a [DexParser] from of any `.dex`, `.class`, `.jar`, `.aar`, or `.apk`. */
    @JvmStatic
    @JvmName("fromFile")
    fun File.toDexParser() = DexParser(listOf(readBytes()))

    /** Create a [DexParser] from of any `.dex`, `.class`, `.jar`, `.aar`, or `.apk`. */
    @JvmStatic
    @JvmName("fromFiles")
    fun Iterable<File>.toDexParser() = DexParser(map { it.readBytes() })

    /** Create a [DexParser] from of any `.dex`, `.class`, `.jar`, `.aar`, or `.apk`. */
    @JvmStatic
    @JvmName("fromBytes")
    fun ByteArray.toDexParser() = DexParser(listOf(copyOf()))

    /** Create a [DexParser] from of any `.dex`, `.class`, `.jar`, `.aar`, or `.apk`. */
    @JvmStatic
    @JvmName("fromBytes")
    fun Iterable<ByteArray>.toDexParser() = DexParser(map { it.copyOf() })
  }
}
