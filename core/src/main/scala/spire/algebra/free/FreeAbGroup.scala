/*
 * **********************************************************************\
 * * Project                                                              **
 * *       ______  ______   __    ______    ____                          **
 * *      / ____/ / __  /  / /   / __  /   / __/     (c) 2011-2021        **
 * *     / /__   / /_/ /  / /   / /_/ /   / /_                            **
 * *    /___  / / ____/  / /   / __  /   / __/   Erik Osheim, Tom Switzer **
 * *   ____/ / / /      / /   / / | |   / /__                             **
 * *  /_____/ /_/      /_/   /_/  |_|  /____/     All rights reserved.    **
 * *                                                                      **
 * *      Redistribution and use permitted under the MIT license.         **
 * *                                                                      **
 * \***********************************************************************
 */

package spire
package algebra
package free

import spire.std.option._
import spire.std.map._
import spire.std.int._
import spire.syntax.rng._

final class FreeAbGroup[A] private (val terms: Map[A, Int]) extends AnyVal { lhs =>

  /**
   * Maps the terms using `f` to type `B` and sums their results using the [[AbGroup]] for `B`.
   */
  def run[B](f: A => B)(implicit B: AbGroup[B]): B =
    terms.foldLeft(B.empty) { case (total, (a, n)) =>
      B.combine(total, B.combineN(f(a), n))
    }

  /**
   * As long as there are no negative terms, this maps the terms using `f`, then sums the results using the [[CMonoid]]
   * for `B`. If there is a negative term, then `None` is returned.
   */
  def runMonoid[B](f: A => B)(implicit B: CMonoid[B]): Option[B] = {
    val it = terms.iterator

    @tailrec def loop(total: B): Option[B] =
      if (it.hasNext) {
        val (a, n) = it.next()
        if (n < 0) None
        else loop(B.combine(total, B.combineN(f(a), n)))
      } else Some(total)

    loop(B.empty)
  }

  /**
   * As long as there are no negative terms and at least 1 positive term, this maps the terms using `f`, then sums the
   * results using the [[CSemigroup]] for `B`. If there is a negative term, or if there are no terms at all, then `None`
   * is returned.
   */
  def runSemigroup[B](f: A => B)(implicit B: CSemigroup[B]): Option[B] = {
    val it = terms.iterator

    @tailrec def loop1(total: B): Option[B] =
      if (it.hasNext) {
        val (a, n) = it.next()
        if (n == 0) loop1(total)
        else if (n < 0) None
        else loop1(B.combine(total, B.combineN(f(a), n)))
      } else Some(total)

    @tailrec def loop0: Option[B] =
      if (it.hasNext) {
        val (a, n) = it.next()
        if (n == 0) loop0
        else if (n < 0) None
        else loop1(B.combineN(f(a), n))
      } else None

    loop0
  }

  /**
   * Sums up the results of the negative and positive terms separately, using `f` to map the terms to type `B` and using
   * its [[CMonoid]]. This returns a tuple with the sum of the negative terms on the left and the sum of the positive
   * terms on the right.
   */
  def split[B](f: A => B)(implicit B: CMonoid[B]): (B, B) =
    terms.foldLeft((B.empty, B.empty)) { case ((ltotal, rtotal), (a, n)) =>
      if (n < 0) (B.combine(ltotal, B.combineN(f(a), -n)), rtotal)
      else if (n > 0) (ltotal, B.combine(rtotal, B.combineN(f(a), n)))
      else (ltotal, rtotal)
    }

  /**
   * Sums up the results of the negative and positive terms separately, using `f` to map the terms to type `B` and using
   * its [[CSemigroup]]. This returns a tuple with the sum of the negative terms on the left and the sum of the positive
   * terms on the right. If either side has no terms at all, then that side is `None`.
   */
  def splitSemigroup[B](f: A => B)(implicit B: CSemigroup[B]): (Option[B], Option[B]) =
    split[Option[B]] { a => Some(f(a)) }

  def |+|(rhs: FreeAbGroup[A]): FreeAbGroup[A] =
    new FreeAbGroup(lhs.terms + rhs.terms)

  def |-|(rhs: FreeAbGroup[A]): FreeAbGroup[A] =
    new FreeAbGroup(lhs.terms - rhs.terms)

  def inverse: FreeAbGroup[A] =
    new FreeAbGroup(-terms)

  override def toString: String =
    if (terms.isEmpty) "e"
    else
      (terms
        .filter(_._2 != 0)
        .collect {
          case (a, n) if n == 1 => a.toString
          case (a, n) if n != 0 => s"($a)^$n"
        }
        .mkString(" |+| "))
}

object FreeAbGroup { companion =>
  final def id[A]: FreeAbGroup[A] = new FreeAbGroup(Map.empty)

  final def apply[A](a: A): FreeAbGroup[A] = lift(a)
  final def lift[A](a: A): FreeAbGroup[A] = new FreeAbGroup[A](Map((a, 1)))

  implicit def FreeAbGroupGroup[A]: AbGroup[FreeAbGroup[A]] = new AbGroup[FreeAbGroup[A]] {
    def empty: FreeAbGroup[A] = companion.id
    def combine(a: FreeAbGroup[A], b: FreeAbGroup[A]): FreeAbGroup[A] = a |+| b
    def inverse(a: FreeAbGroup[A]): FreeAbGroup[A] = a.inverse
    override def remove(a: FreeAbGroup[A], b: FreeAbGroup[A]): FreeAbGroup[A] = a |-| b
  }
}
