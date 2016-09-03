package com.so.typebaselite

import java.util

import com.couchbase.lite.Document.{ChangeEvent, ChangeListener, DocumentUpdater}
import com.couchbase.lite._
import com.so.typebaselite.TblRow._
import com.so.typebaselite.mapper._
import shapeless.Typeable

import scala.collection.JavaConverters._
import scala.collection.Map
import scala.util.control.NonFatal

/**
  * Created by a.reisberg on 8/31/2016.
  */
case class TblDb[Doc](db: Database)(implicit docCodec: Codec.Aux[Doc, JHashMap])
  extends TblQuery[FullRow[String, QueryDocInfo, Doc]] {

  import TblQuery._

  val typeView: TblTypeView[Doc] = createTypeView

  val defaultQuery: FullAux[Query, QueryRow, FullRow[String, QueryDocInfo, Doc]] =
    query()

  def apply(keys: String*): Seq[Doc] = get(keys: _*)

  def apply[D <: Doc](keys: String*)(implicit dTypeable: Typeable[D]): Seq[D] = getType(keys: _*)

  def query(settings: Setting*): FullAux[Query, QueryRow, FullRow[String, QueryDocInfo, Doc]] =
    make[Query, FullRow[String, QueryDocInfo, Doc]](db.createAllDocumentsQuery(), settings)(fullFromQueryRow[String, QueryDocInfo, Doc])

  def get(keys: String*): Seq[Doc] =
    keys.flatMap(key => Option(db.getDocument(key).getProperties) flatMap docCodec.decode)

  def getFull(keys: String*): Seq[(Option[Doc], Document)] = getRaw(keys: _*).map(doc => (docCodec.decode(doc), doc))

  def getRaw(keys: String*): Seq[Document] = keys.flatMap(key => Option(db.getDocument(key)))

  def getType[D <: Doc](keys: String*)(implicit dTypeable: Typeable[D]): Seq[D] =
    get(keys: _*) flatMap dTypeable.cast

  def delete(key: String): Boolean = {
    val doc = db.getDocument(key)
    if (doc != null)
      doc.delete()
    else false
  }

  def put(key: String, value: Doc, overwrite: Boolean): Boolean = {
    val doc = db.getDocument(key)
    val properties = doc.getProperties
    if (properties != null && overwrite) false
    else
      try {
        doc.putProperties(docCodec.encode(value))
        true
      } catch {
        case NonFatal(t) => false
      }
  }

  def put(value: Doc): String = {
    val doc = db.createDocument()
    doc.putProperties(docCodec.encode(value))
    doc.getId
  }

  def update(key: String, value: Doc): Unit = {
    val doc = db.getDocument(key)

    doc.update(new DocumentUpdater {
      override def update(newRevision: UnsavedRevision): Boolean = {
        newRevision.setUserProperties(docCodec.encode(value))
        true
      }
    })
  }

  def addChangeListener[U](id: String)(f: Doc => U): Option[Subscription] = {
    for (doc <- Option(db.getDocument(id))) yield {
      val listener = new ChangeListener {
        override def changed(event: ChangeEvent): Unit = {
          for (d <- docCodec.decode(event.getSource))
            f(d)
        }
      }

      doc.addChangeListener(listener)

      Subscription(() => doc.removeChangeListener(listener))
    }
  }

  def createTypeView: TblTypeView[Doc] = {
    val view = db.getView(typeHintKey)

    view.setMap(new Mapper {
      override def map(document: util.Map[String, AnyRef], emitter: Emitter): Unit = {
        val typeHint = document.get(typeHintKey)
        if (typeHint != null && typeHint.isInstanceOf[String])
          emitter.emit(typeHint.asInstanceOf[String], TblDb.emptyMap)
      }
    }, "0.1")

    new TblTypeView[Doc](view)
  }

  def createIndexView[K](name: String, version: String, mapper: Doc => Set[K])(implicit kCodec: Codec[K]): TblIndexView[K, Doc] = {
    val view = db.getView(name)

    view.setMap(new Mapper {
      override def map(document: util.Map[String, AnyRef], emitter: Emitter): Unit = {
        for (doc <- docCodec.decode(document))
          for (k <- mapper(doc))
            emitter.emit(kCodec.encode(k), TblDb.emptyMap)
      }
    }, version)

    new TblIndexView(view)
  }

  def createMapView[K, V](name: String, version: String, mapper: Doc => Set[(K, V)])(implicit
                                                                                     kCodec: Codec[K],
                                                                                     vCodec: Codec[V]): TblView[K, V, Doc] = {
    val view = db.getView(name)

    view.setMap(new Mapper {
      override def map(document: util.Map[String, AnyRef], emitter: Emitter): Unit = {
        for (t <- docCodec.decode(document))
          for ((k, v) <- mapper(t))
            emitter.emit(kCodec.encode(k), vCodec.encode(v))
      }
    }, version)

    new TblView(view)
  }

  def getExistingView[K, V](name: String)(implicit
                                          kCodec: Codec[K],
                                          vCodec: Codec[V]): Option[TblView[K, String, Doc]] =
    Option(db.getExistingView(name)) map (new TblView(_))

  // Implementing TblQuery.FullAux[Query, QueryRow, KSRow[String, QueryDocInfo, Doc]]
  override type S = Query

  override type E = QueryRow

  override val source: S = defaultQuery.source

  override def sourceAsStream: Stream[QueryRow] = defaultQuery.sourceAsStream

  override def transform: Stream[QueryRow] => Stream[FullRow[String, QueryDocInfo, Doc]] = defaultQuery.transform
}

object TblDb {
  def getCbProperties(document: Document): Map[String, AnyRef] =
    document.getProperties.asScala.filter(_._1.startsWith("_"))

  val emptyMap = new util.HashMap[String, AnyRef]()
}