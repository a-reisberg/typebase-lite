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

/**
  * Wrapper for Couchbase lite's database object.
  *
  * @param db Couchbase lite's database object
  * @param docCodec [[Codec]] to encode/decode [[Doc]] to/from [[JHashMap]]
  * @tparam Doc type of the parsed document
  */
case class TblDb[Doc](db: Database)(implicit docCodec: Codec.Aux[Doc, JHashMap])
  extends TblQuery[FullRow[String, QueryDocInfo, Doc]] {

  import TblQuery._

  /**
    * Automatically generated index for queries by types
    */
  val typeView: TblTypeView[Doc] = createTypeView

  /**
    * The default query. Used internally by the api.
    * End users should use query() instead
    */
  val defaultQuery: FullAux[Query, QueryRow, FullRow[String, QueryDocInfo, Doc]] =
    query()

  /**
    * Get [[Seq[Doc]]] from a sequence of ids.
    *
    * @param ids Sequence of ids
    * @return Sequence of [[Doc]]'s
    */
  def apply(ids: String*): Seq[Doc] = get(ids: _*)

  /**
    * Overloaded version, which automatically casts to [[D]].
    *
    * @param ids Sequence of ids
    * @param dTypeable Shapeless' typeclass to provide safe casting
    * @tparam D Type of the final result
    * @return Sequence of [[D]]'s.
    */
  def apply[D <: Doc](ids: String*)(implicit dTypeable: Typeable[D]): Seq[D] = getType(ids: _*)

  /**
    * Create a query of all documents
    *
    * @param settings Extra settings for this query. See [[Setting]].
    * @return [[TblQuery[FullRow[String, QueryDocInfo, Doc]]]] (the Aux thing is a type refinement)
    */
  def query(settings: Setting*): FullAux[Query, QueryRow, FullRow[String, QueryDocInfo, Doc]] =
    make[Query, FullRow[String, QueryDocInfo, Doc]](db.createAllDocumentsQuery(), settings)(fullFromQueryRow[String, QueryDocInfo, Doc])

  /**
    * Same as [[apply(ids)]] above
    */
  def get(ids: String*): Seq[Doc] =
    ids.flatMap(key => Option(db.getDocument(key)).map(_.getProperties) flatMap docCodec.decode)

  /**
    * Get both the Couchbase's document and the parsed document
    *
    * @param ids Sequence of ids
    * @return A Sequence of Document, along with possible parsed [[Doc]].
    */
  def getFull(ids: String*): Seq[(Option[Doc], Document)] = getRaw(ids: _*).map(doc => (docCodec.decode(doc), doc))

  /**
    * Get the raw document.
    *
    * @param ids Sequence of ids
    * @return Sequence of [[Document]]'s
    */
  def getRaw(ids: String*): Seq[Document] = ids.flatMap(key => Option(db.getDocument(key)))

  /**
    * Same as [[apply[D](ids)]]
    */
  def getType[D <: Doc](ids: String*)(implicit dTypeable: Typeable[D]): Seq[D] =
    get(ids: _*) flatMap dTypeable.cast

  /**
    * Delete the document with the given id.
    *
    * @param id Key of the document to be deleted
    * @return True if is succeeded, False otherwise.
    */
  def delete(id: String): Boolean = {
    val doc = db.getDocument(id)
    if (doc != null)
      doc.delete()
    else false
  }

  /**
    * Add a [[Doc]] to the db using the given id.
    *
    * @param id Provided id
    * @param value Provided value
    * @param overwrite
    * @return True if succeeded, false otherwise
    */
  def put(id: String, value: Doc, overwrite: Boolean): Boolean = {
    val doc = db.getDocument(id)
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

  /**
    * Add a [[Doc]] to the db.
    *
    * @param value Provided value
    * @return id of the inserted doc
    */
  def put(value: Doc): String = {
    val doc = db.createDocument()
    doc.putProperties(docCodec.encode(value))
    doc.getId
  }

  def put[D <: Doc](fillId: String => D): D = {
    val doc = db.createDocument()
    val filled = fillId(doc.getId)
    doc.putProperties(docCodec.encode(filled))
    filled
  }

  /**
    * Replace the document at the given id by the given value.
    * Create new if non existed before.
    *
    * @param id Provided id
    * @param value Provided value
    */
  def update(id: String, value: Doc): Unit = {
    val doc = db.getDocument(id)

    doc.update(new DocumentUpdater {
      override def update(newRevision: UnsavedRevision): Boolean = {
        newRevision.setUserProperties(docCodec.encode(value))
        true
      }
    })
  }

  /**
    * Add change listener to the document with the given id
    *
    * @param id Provided id
    * @param f Callback function when the document changes
    * @tparam U Return type of f
    * @return Some(subscription) if succeeded, Non otherwise.
    */
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

  /**
    * Function to automatically create a type view, used to query by types.
    *
    * @return a [[TblTypeView]], indexed by the name of the type
    */
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

  /**
    * Create an index (i.e. View) with the provided name, version, and mapper.
    * This View has empty body (hence, just forward to the source document when query).
    *
    * @param name Provided name for the view
    * @param version Provided version of the view
    * @param mapper Function to create a set of keys [[K]] that should be emitted on a given [[Doc]]
    * @param kCodec Codec used to encode [[K]]
    * @tparam K Type of the keys
    * @return A [[TblIndexView[K, Doc]]]
    */
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

  /**
    * Similar to the [[createIndexView()]], except that now, we can specify which document it should redirect to.
    *
    * @param name Provided name of for the view
    * @param version Provided version of the vew
    * @param mapper Function to create a set of [[(K, String)]],
    *               where [[K]] is the key of the index
    *               and [[String]] is the id of the document that should be redirected to
    * @param kCodec Codec used to encode [[K]]
    * @tparam K Type of the keys
    * @return A [[TblIndexView[K, Doc]]]
    */
  def createRedirectView[K](name: String, version: String, mapper: Doc => Set[(K, String)])(implicit kCodec: Codec[K]): TblIndexView[K, Doc] = {
    val view = db.getView(name)

    view.setMap(new Mapper {
      override def map(document: util.Map[String, AnyRef], emitter: Emitter): Unit = {
        for (doc <- docCodec.decode(document))
          for ((k, id) <- mapper(doc))
            emitter.emit(kCodec.encode(k), TblDb.redirectMap(id))
      }
    }, version)

    new TblIndexView(view)
  }

  /**
    * Most general MapView creation facility
    *
    * @param name Provided name for the view
    * @param version Provided version of the view
    * @param mapper Function to create a set of keys (of type [[K]]) and values (of type [[V]])
    *               to be emitted
    * @param kCodec Codec to encode [[K]]
    * @param vCodec Codec to encode [[V]]
    * @tparam K Type of keys
    * @tparam V Type of values
    * @return A [[TblView[K, V, Doc]]]
    */
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

  /**
    * Get an existing vew by the given name
    *
    * @param name Provided name of the view
    * @param kCodec Codec for [[K]]
    * @param vCodec Codec for [[V]]
    * @tparam K Type of keys
    * @tparam V Type of values
    * @return A [[TblView[K, String, Doc]]] (Option)
    */
  def getExistingView[K, V](name: String)(implicit
                                          kCodec: Codec[K],
                                          vCodec: Codec[V]): Option[TblView[K, String, Doc]] =
    Option(db.getExistingView(name)) map (new TblView(_))

  // Implementing TblQuery.FullAux[Query, QueryRow, FullRow[String, QueryDocInfo, Doc]]
  override type S = Query

  override type E = QueryRow

  override val source: S = defaultQuery.source

  override def sourceAsStream: Stream[QueryRow] = defaultQuery.sourceAsStream

  override def transform: Stream[QueryRow] => Stream[FullRow[String, QueryDocInfo, Doc]] = defaultQuery.transform
}

object TblDb {
  /**
    * Exclude user's properties
    *
    * @param document Couchbase lite's document
    * @return A Map which only contains Couchbase lite's properties
    */
  def getCbProperties(document: Document): Map[String, AnyRef] =
    document.getProperties.asScala.filter(_._1.startsWith("_"))

  val emptyMap: util.HashMap[String, AnyRef] = new util.HashMap[String, AnyRef]()

  def redirectMap(id: String): util.HashMap[String, AnyRef] = {
    val map = new util.HashMap[String, AnyRef]()
    map.put("_id", id)
    map
  }
}