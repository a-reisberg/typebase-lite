package io.typebase.lite

import com.couchbase.lite._
import io.typebase.lite.TblQuery._
import io.typebase.lite.TblRow._
import io.typebase.lite.mapper._
import io.typebase.lite.mapper.{Codec, NameHelper}


/**
  * Created by a.reisberg on 8/31/2016.
  */
class TblView[K, V, S](view: View)(implicit
                                   kCodec: Codec[K],
                                   vCodec: Codec[V],
                                   sCodec: Codec[S]) {
  def getFull(ks: K*): FullAux[Query, QueryRow, FullRow[K, V, S]] =
    fullQuery(withKeys[K](ks: _*))

  def getKV(ks: K*): FullAux[Query, QueryRow, KVRow[K, V]] =
    kvQuery(withKeys[K](ks: _*))

  def getKS(ks: K*): FullAux[Query, QueryRow, KSRow[K, S]] =
    ksQuery(withKeys[K](ks: _*))

  def getV(ks: K*): FullAux[Query, QueryRow, V] =
    vQuery(withKeys[K](ks: _*))

  def getS(ks: K*): FullAux[Query, QueryRow, S] =
    sQuery(withKeys[K](ks: _*))

  def fullQuery(settings: Setting*): FullAux[Query, QueryRow, FullRow[K, V, S]] =
    make(view.createQuery, settings)(fullFromQueryRow[K, V, S])

  def fullLiveQuery(settings: Setting*): FullAux[LiveQuery, QueryRow, FullRow[K, V, S]] =
    make(view.createQuery.toLiveQuery, settings)(fullFromQueryRow[K, V, S])

  def kvQuery(settings: Setting*): FullAux[Query, QueryRow, KVRow[K, V]] =
    make(view.createQuery, settings)(kvFromQueryRow[K, V])

  def kvLiveQuery(settings: Setting*): FullAux[LiveQuery, QueryRow, KVRow[K, V]] =
    make(view.createQuery.toLiveQuery, settings)(kvFromQueryRow[K, V])

  def ksQuery(settings: Setting*): FullAux[Query, QueryRow, KSRow[K, S]] =
    make(view.createQuery, settings)(ksFromQueryRow[K, S])

  def ksLiveQuery(settings: Setting*): FullAux[LiveQuery, QueryRow, KSRow[K, S]] =
    make(view.createQuery.toLiveQuery, settings)(ksFromQueryRow[K, S])

  def kQuery(settings: Setting*): FullAux[Query, QueryRow, K] =
    make(view.createQuery, settings)(kFromQueryRow[K])

  def kLiveQuery(settings: Setting*): FullAux[LiveQuery, QueryRow, K] =
    make(view.createQuery.toLiveQuery, settings)(kFromQueryRow[K])

  def vQuery(settings: Setting*): FullAux[Query, QueryRow, V] =
    make(view.createQuery, settings)(vFromQueryRow[V])

  def vLiveQuery(settings: Setting*): FullAux[LiveQuery, QueryRow, V] =
    make(view.createQuery.toLiveQuery, settings)(vFromQueryRow[V])

  def sQuery(settings: Setting*): FullAux[Query, QueryRow, S] =
    make(view.createQuery, settings)(sFromQueryRow[S])

  def sLiveQuery(settings: Setting*): FullAux[LiveQuery, QueryRow, S] =
    make(view.createQuery.toLiveQuery, settings)(sFromQueryRow[S])
}

class TblFullView[K, V, S](view: View)(implicit
                                       kCodec: Codec[K],
                                       vCodec: Codec[V],
                                       sCodec: Codec[S])
  extends TblView[K, V, S](view)(kCodec, vCodec, sCodec) {

  def apply(settings: Setting*): FullAux[Query, QueryRow, FullRow[K, V, S]] =
    fullQuery(settings: _*)
}

class TblIndexView[K, S](view: View)(implicit
                                     kCodec: Codec[K],
                                     sCodec: Codec[S])
  extends TblView[K, Empty, S](view) {

  def apply(settings: Setting*): FullAux[Query, QueryRow, S] =
    sQuery(settings: _*)
}

class TblTypeView[S](view: View)(implicit sCodec: Codec[S])
  extends TblIndexView[String, S](view) {

  def apply[K](implicit
               kNameHelper: NameHelper[K],
               kCodec: Codec[K]): FullAux[Query, QueryRow, K] = ofType[K]

  def ofType[K](implicit
                kNameHelper: NameHelper[K],
                kCodec: Codec[K]): FullAux[Query, QueryRow, K] =
    make(view.createQuery, Seq(typeKeys[K]))(sFromQueryRow[K])
}
