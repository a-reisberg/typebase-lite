package io.typebase.lite

import com.couchbase.lite.{Document, QueryRow}
import io.typebase.lite.mapper.Codec

/**
  * Created by a.reisberg on 8/31/2016.
  */
sealed trait TblRow

case class KSRow[K, S](k: K, src: S, srcId: String, srcDoc: Document) extends TblRow

case class KVRow[K, V](k: K, v: V, srcId: String) extends TblRow

case class FullRow[K, V, S](k: K, v: V, src: S, srcId: String, srcDoc: Document) extends TblRow

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
