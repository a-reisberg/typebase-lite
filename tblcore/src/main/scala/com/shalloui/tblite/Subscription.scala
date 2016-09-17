package com.shalloui.tblite

/**
  * Created by a.reisberg on 8/31/2016.
  */
trait Subscription {
  self =>
  def dispose(): Unit

  def +(s: Subscription): Subscription = new Subscription {
    override def dispose(): Unit = {
      self.dispose()
      s.dispose()
    }
  }
}

object Subscription {
  def apply(f: () => Unit): Subscription = new Subscription {
    override def dispose(): Unit = f()
  }
}
