package com.so.typebaselite

import java.util

import com.couchbase.lite.{Query, QueryRow}
import com.so.typebaselite.mapper._
import shapeless.Typeable

import scala.collection.JavaConverters._
import scala.collection.generic.CanBuildFrom
import scala.language.{higherKinds, implicitConversions}

/**
  * Created by a.reisberg on 8/31/2016.
  */
trait TblQuery[A] {
  self =>

  import TblQuery._

  // Type of the source. Usually, this is Scala's collection type, or Couchbase lite's Query.
  type S

  // Type of elements in the source. If coming from Scala's collections,
  // for eg. List[A], then E is just A. If this comes from Couchbase lite,
  // then it would be of type QueryRow.
  type E

  val source: S

  def sourceAsStream: Stream[E]

  def transform: Stream[E] => Stream[A]

  def subscribe(f: Stream[A] => Unit)(implicit ev: Subscribable.Aux[S, Stream[E]]): Subscription =
  ev.subscribe(source)(es => f(transform(es)))

  def start()(implicit ev: Subscribable[S]): Unit = ev.start(source)

  def stop()(implicit ev: Subscribable[S]): Unit = ev.stop(source)

  def run: Stream[A] = transform(sourceAsStream)

  def iterator: Iterator[A] = run.iterator

  def foreach[U](f: A => U): Unit = iterator.foreach(f)

  def where(p: A => Boolean): FullAux[S, E, A] = filter(p)

  def map[B, BS <: Stream[B]](f: A => B)(implicit cbf: CanBuildFrom[Stream[A], B, BS]): FullAux[S, E, B] =
    lift(_.map[B, BS](f))

  def flatMap[B](f: A => TblQuery[B]): FullAux[S, E, B] =
    lift(_.flatMap(f(_).run))

  def zip[B](qB: TblQuery[B]): FullAux[S, E, (A, B)] =
    lift(_.zip(qB.run))

  def collect[B, BS <: Stream[B]](p: PartialFunction[A, B])(implicit cbf: CanBuildFrom[Stream[A], B, BS]): FullAux[S, E, B] =
    lift(_.collect(p))

  def collectFirst[B](p: PartialFunction[A, B]): FullAux[S, E, B] =
    lift(_.collectFirst(p).toStream)

  def filter(p: A => Boolean): FullAux[S, E, A] =
    lift(_.filter(p))

  def take(n: Int): FullAux[S, E, A] =
    lift(_.take(n))

  def drop(n: Int): FullAux[S, E, A] =
    lift(_.drop(n))

  def reduce[A1 >: A](op: (A1, A1) => A1): FullAux[S, E, A1] =
    lift(st => Stream(st.reduce(op)))

  def foldLeft[B](z: B)(op: (B, A) => B): FullAux[S, E, B] =
    lift(st => Stream(st.foldLeft(z)(op)))

  def foldRight[B](z: B)(op: (A, B) => B): FullAux[S, E, B] =
    lift(st => Stream(st.foldRight(z)(op)))

  def takeWhile(p: A => Boolean): FullAux[S, E, A] =
    lift(_.takeWhile(p))

  def dropWhile(p: A => Boolean): FullAux[S, E, A] =
    lift(_.dropWhile(p))

  def extract[B](implicit extractB: Extract[A, B]): FullAux[S, E, B] =
    lift(_.flatMap(extractB.apply))

  def extractType[B <: A](implicit bTypeable: Typeable[B]): FullAux[S, E, B] =
    lift(_.flatMap(bTypeable.cast))

  def hasDefiniteSize: Boolean = false

  def groupBy[K](f: A => K): FullAux[S, E, (K, Stream[A])] =
    lift(_.groupBy(f).toStream)

  def seq: TraversableOnce[A] = run

  def lift[B, BS <: Stream[B]](f: Stream[A] => BS): FullAux[S, E, B] = new TblQuery[B] {
    override type S = self.S

    override type E = self.E

    override val source: S = self.source

    override def sourceAsStream: Stream[E] = self.sourceAsStream

    override def transform: Stream[E] => BS = f compose self.transform
  }
}

object TblQuery {
  type Setting = Query => Unit

  type Aux[S0, A] = TblQuery[A] {type S = S0}

  type FullAux[S0, E0, A] = TblQuery[A] {
    type S = S0
    type E = E0
  }

  // Construct a query out of a value of type S and a transformation of S to Iterable[A].
  // s will be evaluated strictly: put into the source and ev is in the transform
  // Main audience of this is to convert from Scala's collections.
  def apply[S0, A](s: S0)(implicit f: S0 => Iterable[A]): FullAux[S0, A, A] = apply[S0, A, A](s, identity[Stream[A]])(f)

  /**
    *
    * @param s      The source, either a Scala's collection or a Couchbase lite's Query.
    *               If it's the former, use the other overloaded version of [[apply()]] for simplicitly.
    * @param transf Function providing transformation to the target type (from Couchbase lite's Query).
    * @param ev     Convert to Iterable (no more extra processing, for eg. parsing)
    * @tparam S0 Type of the provide source s
    * @tparam E0 Type of each element in the resulting conversion to Iterable by ev.
    *            Usually, this would be QueryRow, when we feed in Couchbase's query
    * @tparam B  Type of each element in the resluting TblQuery.
    * @return TblQuery[B]
    */
  def apply[S0, E0, B](s: S0, transf: Stream[E0] => Stream[B])(ev: S0 => Iterable[E0]): FullAux[S0, E0, B] = new TblQuery[B] {
    override type S = S0

    override type E = E0

    override val source: S = s

    override def sourceAsStream: Stream[E] = ev(s).toStream

    override def transform: Stream[E] => Stream[B] = transf
  }

  implicit def toTblQuery[S, A](s: S)(implicit ev: S => Iterable[A]): FullAux[S, A, A] =
    TblQuery[S, A](s)

  def query2Stream[Q <: Query](query: Q): Stream[QueryRow] =
    query.run().iterator().asScala.toStream

  def make[Q <: Query, X](query: Q, settings: Seq[Setting])(f: QueryRow => Option[X]): FullAux[Q, QueryRow, X] =
    TblQuery[Q, QueryRow, X](applySettings(query, settings), _.flatMap(f(_)))(query2Stream(_))

  def withKeys[K](ks: K*)(implicit kCodec: Codec[K]): Setting = query => {
    val l: util.ArrayList[AnyRef] = new util.ArrayList[AnyRef]()
    for (k <- ks) l.add(kCodec.encode(k).asInstanceOf[AnyRef])
    query.setKeys(l)
  }

  def startKey[K](k: K)(implicit kCodec: Codec[K]): Setting = query => {
    query.setStartKey(kCodec.encode(k))
  }

  def endKey[K](k: K)(implicit kCodec: Codec[K]): Setting = query => {
    query.setEndKey(kCodec.encode(k))
  }

  def docIdStartKey(k: String): Setting = query => {
    query.setStartKeyDocId(k)
  }

  def docIdEndKey(k: String): Setting = query => {
    query.setEndKeyDocId(k)
  }

  // Should only be used with TblTypeView. This create a list of string keys consisting
  // of names of (concrete) subclasses of K.
  def typeKeys[K](implicit kNameHelper: NameHelper[K]): Setting =
  withKeys[String](kNameHelper().toSeq: _*)

  def applySettings[Q <: Query](q: Q, fs: Seq[Setting]): Q = {
    for (f <- fs) f(q)
    q
  }
}