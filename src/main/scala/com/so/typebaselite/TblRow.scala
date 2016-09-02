package com.so.typebaselite

import com.couchbase.lite.{Document, QueryRow}
import com.so.typebaselite.mapper.Codec
import shapeless.Typeable

/**
  * Created by a.reisberg on 8/31/2016.
  */
sealed trait TblRow

case class KSRow[K, S](k: K, src: S, srcId: String, srcDoc: Document) extends TblRow {
  def kIs[K2 <: K](implicit k2Typeable: Typeable[K2]): Option[KSRow[K2, S]] =
    for (k2 <- k2Typeable.cast(k))
      yield KSRow(k2, src, srcId, srcDoc)

  def sIs[S2 <: S](implicit s2Typeable: Typeable[S2]): Option[KSRow[K, S2]] =
    for (src2 <- s2Typeable.cast(src))
      yield KSRow(k, src2, srcId, srcDoc)
}

case class KVRow[K, V](k: K, v: V, srcId: String) extends TblRow {
  def kIs[K2 <: K](implicit k2Typeable: Typeable[K2]): Option[KVRow[K2, V]] =
    for (k2 <- k2Typeable.cast(k))
      yield KVRow(k2, v, srcId)

  def vIs[V2 <: V](implicit v2Typeable: Typeable[V2]): Option[KVRow[K, V2]] =
    for (v2 <- v2Typeable.cast(v))
      yield KVRow(k, v2, srcId)
}

case class FullRow[K, V, S](k: K, v: V, src: S, srcId: String, srcDoc: Document) extends TblRow {
  def kIs[K2 <: K](implicit k2Typeable: Typeable[K2]): Option[FullRow[K2, V, S]] =
    for (k2 <- k2Typeable.cast(k))
      yield FullRow(k2, v, src, srcId, srcDoc)

  def vIs[V2 <: V](implicit k2Typeable: Typeable[V2]): Option[FullRow[K, V2, S]] =
    for (v2 <- k2Typeable.cast(v))
      yield FullRow(k, v2, src, srcId, srcDoc)

  def sIs[S2 <: S](implicit s2Typeable: Typeable[S2]): Option[FullRow[K, V, S2]] =
    for (src2 <- s2Typeable.cast(src))
      yield FullRow(k, v, src2, srcId, srcDoc)

  def toKVRow[K2, V2](implicit k2Typeable: Typeable[K2], v2Typeable: Typeable[V2]): Option[KVRow[K2, V2]] =
    for {
      k2 <- k2Typeable.cast(k)
      v2 <- v2Typeable.cast(v)
    } yield KVRow(k2, v2, srcId)

  def toKSRow[K2, S2](implicit k2Typeable: Typeable[K2], s2Typeable: Typeable[S2]): Option[KSRow[K2, S2]] =
    for {
      k2 <- k2Typeable.cast(k)
      src2 <- s2Typeable.cast(src)
    } yield KSRow(k2, src2, srcId, srcDoc)
}

object TblRow {
  def fullFromQueryRow[K, V, S](row: QueryRow)(implicit
                                               kCodec: Codec[K],
                                               vCodec: Codec[V],
                                               sCodec: Codec[S]): Option[FullRow[K, V, S]] =
    for {
      id <- Option(row.getDocumentId)
      doc <- Option(row.getDocument)
      k <- kCodec.decode(row.getKey)
      v <- vCodec.decode(row.getValue)
      s <- sCodec.decode(doc.getProperties)
    } yield FullRow(k, v, s, id, doc)

  def kvFromQueryRow[K, V](row: QueryRow)(implicit
                                          kCodec: Codec[K],
                                          vCodec: Codec[V]): Option[KVRow[K, V]] =
    for {
      id <- Option(row.getDocumentId)
      k <- kCodec.decode(row.getKey)
      v <- vCodec.decode(row.getValue)
    } yield KVRow(k, v, id)

  def ksFromQueryRow[K, S](row: QueryRow)(implicit
                                          kCodec: Codec[K],
                                          sCodec: Codec[S]): Option[KSRow[K, S]] = {
    for {
      id <- Option(row.getDocumentId)
      doc <- Option(row.getDocument)
      k <- kCodec.decode(row.getKey)
      s <- sCodec.decode(doc.getProperties)
    } yield KSRow(k, s, id, doc)
  }

  def kFromQueryRow[K](row: QueryRow)(implicit kCodec: Codec[K]): Option[K] =
    for {
      k <- kCodec.decode(row.getKey)
    } yield k

  def vFromQueryRow[V](row: QueryRow)(implicit vCodec: Codec[V]): Option[V] =
    for {
      v <- vCodec.decode(row.getValue)
    } yield v

  def sFromQueryRow[S](row: QueryRow)(implicit sCodec: Codec[S]): Option[S] =
    for {
      doc <- Option(row.getDocument)
      src <- sCodec.decode(doc.getProperties)
    } yield src
}
