/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package xsbt

import collection.mutable.Map
import scala.tools.nsc.Global

/**
 * A memoized lookup of an enclosing non local class.
 *
 * Let's consider an example of an owner chain:
 *
 *   pkg1 <- pkg2 <- class A <- object B <- class C <- def foo <- class Foo <- class Bar
 *
 * For an object, we work with its `moduleClass` so we can refer to everything as classes.
 *
 * Classes A, B, C are non local so they are mapped to themselves. Classes Foo and Bar are local because
 * they are defined within method `foo`.
 *
 * Let's define non local class more precisely. A non local class is a class that is owned by either a package
 * or another non local class. This gives rise to a recursive definition of a non local class that is used in the
 * implementation of the mapping.
 *
 * Thanks to memoization, the amortized cost of a lookup is O(1). We amortize over lookups of all class symbols
 * in the current compilation run.
 *
 * Additionally, you can query whether a given class is local. Check `isLocal`'s documentation.
 */
class LocalToNonLocalClass[G <: Global](val global: G) {
  import global._
  private val cache: Map[Symbol, Symbol] = perRunCaches.newMap()

  def resolveNonLocal(s: Symbol): Symbol = {
//    assert(
//      phase.id <= sbtDependency.ownPhase.id,
//      s"Tried to resolve ${s.fullName} to a  non local classes but the resolution works up to sbtDependency phase. We're at ${phase.name}"
//    )
    resolveCached(s)
  }

  /**
   * Queries the cached information whether a class is a local class. If there's no cached information about
   * the class None is returned.
   *
   * This method doesn't mutate the cache.
   */
  def isLocal(s: Symbol): Option[Boolean] = {
    assert(s.isClass, s"The ${s.fullName} is not a class.")
    cache.get(s).map(_ != s)
  }

  private def resolveCached(s: Symbol): Symbol = {
    assert(s.isClass, s"The ${s.fullName} is not a class.")
    cache.getOrElseUpdate(s, lookupNonLocal(s))
  }
  private def lookupNonLocal(s: Symbol): Symbol = {
    if (s.owner.isPackageClass) s
    else if (s.owner.isClass) {
      val nonLocalForOwner = resolveCached(s.owner)
      // the s is owned by a non local class so s is non local
      if (nonLocalForOwner == s.owner) s
      // otherwise the inner most non local class is the same as for its owner
      else nonLocalForOwner
    } else resolveCached(s.owner.enclClass)
  }
}
