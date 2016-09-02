package com.so.typebaselite

import com.couchbase.lite.LiveQuery._
import com.couchbase.lite._

import scala.collection.JavaConverters._

/**
  * Type class witnessing the fact that one can listen
  * for events of type E from a source of type S.
  *
  * Created by a.reisberg on 8/31/2016.
  */
trait Subscribable[S] {
  type E

  def subscribe(s: S)(listener: E => Unit): Subscription

  def start(s: S): Unit

  def stop(s: S): Unit
}

object Subscribable {
  type Aux[S, E0] = Subscribable[S] {type E = E0}

  implicit val liveQueryIsSubscribable: Aux[LiveQuery, Stream[QueryRow]] =
    new Subscribable[LiveQuery] {
      type E = Stream[QueryRow]

      override def subscribe(s: LiveQuery)(listener: E => Unit): Subscription = {
        val changeListener = new ChangeListener {
          override def changed(event: ChangeEvent): Unit = {
            listener(event.getRows.iterator().asScala.toStream)
          }
        }

        s.addChangeListener(changeListener)

        Subscription(() => s.removeChangeListener(changeListener))
      }

      override def start(s: LiveQuery): Unit = s.start()

      override def stop(s: LiveQuery): Unit = s.stop()
    }
}