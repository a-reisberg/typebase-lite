package io.typebase.lite

import java.util

import com.couchbase.lite.Document.{ChangeEvent, ChangeListener, DocumentUpdater}
import com.couchbase.lite._
import io.typebase.lite.TblRow._
import io.typebase.lite.mapper._
import io.typebase.lite.mapper.Codec
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
  * @param db       Couchbase lite's database object
  * @param docCodec [[Codec]] to encode/decode [[Doc]] to/from [[JHashMap]]
  * @tparam Doc type of the parsed document
  */
case class TblDb[Doc](db: Database)(implicit docCodec: Codec.Aux[Doc, JHashMap])
  extends TblQuery[FullRow[String, QueryDocInfo, Doc]] {

  import TblDb._
  import TblQuery._

  /**
    * Automatically generated index for queries by types
    */
  val typeView: TblTypeView[Doc] = createTypeView

  /**
    * The default query. Used internally by the api.
    * End users should use query() instead
    */
  val defaultQuery: FullAux[Query, QueryRow, FullRow[String, QueryDocInfo, Doc]] = query()

  def apply(settings: Setting*): FullAux[Query, QueryRow, FullRow[String, QueryDocInfo, Doc]] =
    query(settings: _*)

  /**
    * Create a query of all documents
    *
    * @param settings Extra settings for this query. See [[Setting]].
    * @return [[TblQuery[FullRow[String, QueryDocInfo, Doc]]]] (the Aux thing is a type refinement)
    **/
  def query(settings: Setting*): FullAux[Query, QueryRow, FullRow[String, QueryDocInfo, Doc]] =
  make[Query, FullRow[String, QueryDocInfo, Doc]](db.createAllDocumentsQuery(), settings)(fullFromQueryRow[String, QueryDocInfo, Doc])

  /**
    * Same as [[apply(ids)]] above
    */
  def get(ids: String*): List[Doc] = getHelper(ids: _*)

  /**
    * Same as [[apply[D](ids)]]
    */
  def getType[D <: Doc](ids: String*)(implicit dTypeable: Typeable[D]): List[D] =
  getHelper(ids: _*) flatMap dTypeable.cast

  /**
    * Get both the Couchbase's document and the parsed document
    *
    * @param ids Sequence of ids
    * @return A Sequence of Document, along with possible parsed [[Doc]].
    */
  def getFull(ids: String*): List[(Option[Doc], Document)] = getRaw(ids: _*).map(doc => (docCodec.decode(doc), doc))

  /**
    * Get the raw document.
    *
    * @param ids Sequence of ids
    * @return Sequence of [[Document]]'s
    */
  def getRaw(ids: String*): List[Document] = ids.toList.flatMap(key => Option(db.getDocument(key)))

  def getHelper(ids: String*): List[Doc] =
    ids.toList.flatMap(key => Option(db.getDocument(key)).map(_.getProperties) flatMap docCodec.decode)

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
    * @param id    Provided id
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
        doc.putProperties(removeCbProperties(docCodec.encode(value)))
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
    doc.putProperties(removeCbProperties(docCodec.encode(value)))
    doc.getId
  }

  def put[D <: Doc](fillId: String => D): D = {
    val doc = db.createDocument()
    val filled = fillId(doc.getId)
    doc.putProperties(removeCbProperties(docCodec.encode(filled)))
    filled
  }

  /**
    * Replace the document at the given id by the given value.
    * Create new if non existed before.
    *
    * @param id    Provided id
    * @param value Provided value
    */
  def update(id: String, value: Doc): Unit = {
    val doc = db.getDocument(id)

    doc.update(new DocumentUpdater {
      override def update(newRevision: UnsavedRevision): Boolean = {
        newRevision.setUserProperties(removeCbProperties(docCodec.encode(value)))
        true
      }
    })
  }

  /**
    * Add change listener to the document with the given id
    *
    * @param id Provided id
    * @param f  Callback function when the document changes
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
          emitter.emit(typeHint.asInstanceOf[String], emptyMap)
      }
    }, "0.1")

    new TblTypeView[Doc](view)
  }

  /**
    * Create an index (i.e. View) with the provided name, version, and mapper.
    * This View has empty body (hence, just forward to the source document when query).
    *
    * @param name    Provided name for the view
    * @param version Provided version of the view
    * @param mapper  Function to create a set of keys [[K]] that should be emitted on a given [[Doc]]
    * @param kCodec  Codec used to encode [[K]]
    * @tparam K Type of the keys
    * @return A [[TblIndexView[K, Doc]]]
    */
  def createIndex[K](name: String, version: String, mapper: Doc => Set[K])(implicit kCodec: Codec[K]): TblIndexView[K, Doc] = {
    val view = db.getView(name)

    view.setMap(new Mapper {
      override def map(document: util.Map[String, AnyRef], emitter: Emitter): Unit = {
        for (doc <- docCodec.decode(document))
          for (k <- mapper(doc))
            emitter.emit(kCodec.encode(k), emptyMap)
      }
    }, version)

    new TblIndexView(view)
  }

  def createIndex[K, D](name: String, version: String, mapper: Doc => Set[K])(implicit
                                                                              kCodec: Codec[K],
                                                                              dCodec: Codec[D],
                                                                              dTypeable: Typeable[D]): TblIndexView[K, D] = {
    val view = db.getView(name)

    view.setMap(new Mapper {
      override def map(document: util.Map[String, AnyRef], emitter: Emitter): Unit = {
        for (doc <- docCodec.decode(document))
          if (dTypeable.cast(doc).isDefined)
            for (k <- mapper(doc))
              emitter.emit(kCodec.encode(k), emptyMap)
      }
    }, version)

    new TblIndexView(view)
  }

  /**
    * Similar to the [[createIndex()]], except that now, we can specify which document it should redirect to.
    *
    * @param name    Provided name of for the view
    * @param version Provided version of the vew
    * @param mapper  Function to create a set of [[(K, String)]],
    *                where [[K]] is the key of the index
    *                and [[String]] is the id of the document that should be redirected to
    * @param kCodec  Codec used to encode [[K]]
    * @tparam K Type of the keys
    * @return A [[TblIndexView[K, Doc]]]
    */
  def createRedirectView[K](name: String, version: String, mapper: Doc => Set[(K, String)])(implicit kCodec: Codec[K]): TblIndexView[K, Doc] = {
    val view = db.getView(name)

    view.setMap(new Mapper {
      override def map(document: util.Map[String, AnyRef], emitter: Emitter): Unit = {
        for (doc <- docCodec.decode(document))
          for ((k, id) <- mapper(doc))
            emitter.emit(kCodec.encode(k), redirectMap(id))
      }
    }, version)

    new TblIndexView(view)
  }

  def createRedirectView[K, D](name: String, version: String, mapper: Doc => Set[(K, String)])(implicit
                                                                                               kCodec: Codec[K],
                                                                                               dCodec: Codec[D]): TblIndexView[K, D] = {
    val view = db.getView(name)

    view.setMap(new Mapper {
      override def map(document: util.Map[String, AnyRef], emitter: Emitter): Unit = {
        for (doc <- docCodec.decode(document))
          for ((k, id) <- mapper(doc))
            if (dCodec.decode(db.getDocument(id).getProperties).isDefined)
              emitter.emit(kCodec.encode(k), redirectMap(id))
      }
    }, version)

    new TblIndexView(view)
  }

  /**
    * Most general MapView creation facility
    *
    * @param name    Provided name for the view
    * @param version Provided version of the view
    * @param mapper  Function to create a set of keys (of type [[K]]) and values (of type [[V]])
    *                to be emitted
    * @param kCodec  Codec to encode [[K]]
    * @param vCodec  Codec to encode [[V]]
    * @tparam K Type of keys
    * @tparam V Type of values
    * @return A [[TblFullView[K, V, Doc]]]
    */
  def createMapView[K, V](name: String, version: String, mapper: Doc => Set[(K, V)])(implicit
                                                                                     kCodec: Codec[K],
                                                                                     vCodec: Codec[V]): TblFullView[K, V, Doc] = {
    val view = db.getView(name)

    view.setMap(new Mapper {
      override def map(document: util.Map[String, AnyRef], emitter: Emitter): Unit = {
        for (t <- docCodec.decode(document))
          for ((k, v) <- mapper(t))
            emitter.emit(kCodec.encode(k), vCodec.encode(v))
      }
    }, version)

    new TblFullView(view)
  }

  /**
    * Get an existing vew by the given name
    *
    * @param name   Provided name of the view
    * @param kCodec Codec for [[K]]
    * @param vCodec Codec for [[V]]
    * @tparam K Type of keys
    * @tparam V Type of values
    * @return A [[TblFullView[K, String, Doc]]] (Option)
    */
  def getView[K, V](name: String)(implicit
                                  kCodec: Codec[K],
                                  vCodec: Codec[V]): Option[TblFullView[K, String, Doc]] =
  Option(db.getExistingView(name)) map (new TblFullView(_))

  def getIndex[K](name: String)(implicit kCodec: Codec[K]): Option[TblIndexView[K, Doc]] =
    Option(db.getExistingView(name)) map (new TblIndexView(_))

  def getIndex[K, D](name: String)(implicit
                                   kCodec: Codec[K],
                                   dCodec: Codec[D]): Option[TblIndexView[K, D]] =
    Option(db.getExistingView(name)) map (new TblIndexView(_))

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

  def removeCbProperties(map: JHashMap): JHashMap = {
    val res = new JHashMap
    map.asScala.foreach { case (k, v) => if (!k.startsWith("_")) res.put(k, v) }
    res
  }

  val emptyMap: util.HashMap[String, AnyRef] = new util.HashMap[String, AnyRef]()

  def redirectMap(id: String): util.HashMap[String, AnyRef] = {
    val map = new util.HashMap[String, AnyRef]()
    map.put("_id", id)
    map
  }
}