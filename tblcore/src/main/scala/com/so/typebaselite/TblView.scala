package com.so.typebaselite

import com.couchbase.lite.{LiveQuery, Query, QueryRow, View}
import com.so.typebaselite.TblQuery._
import com.so.typebaselite.TblRow._
import com.so.typebaselite.mapper.{Codec, NameHelper}
import shapeless.Typeable


/**
  * Created by a.reisberg on 8/31/2016.
  */
class TblView[K, V, S](view: View)(implicit
                                   kCodec: Codec[K],
                                   vCodec: Codec[V],
                                   sCodec: Codec[S]) {
  def apply(ks: K*): FullAux[Query, QueryRow, V] =
    make(view.createQuery(), Seq(withKeys[K](ks: _*)))(vFromQueryRow[V])

  def apply[V2](ks: K*)(implicit v2Typeable: Typeable[V2]): FullAux[Query, QueryRow, V2] =
    make(view.createQuery(), Seq(withKeys[K](ks: _*)))(vFromQueryRow[V](_).flatMap(v2Typeable.cast))

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

class TblIndexView[K, S](view: View)(implicit
                                     kCodec: Codec[K],
                                     sCodec: Codec[S])
  extends TblView[K, Empty, S](view) {

  override def apply[S2](ks: K*)(implicit s2Typeable: Typeable[S2]): FullAux[Query, QueryRow, S2] =
    make(view.createQuery(), Seq(withKeys[K](ks: _*)))(sFromQueryRow[S](_).flatMap(s2Typeable.cast))
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
