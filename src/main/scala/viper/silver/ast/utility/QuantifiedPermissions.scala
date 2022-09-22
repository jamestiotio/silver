// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at http://mozilla.org/MPL/2.0/.
//
// Copyright (c) 2011-2019 ETH Zurich.

package viper.silver.ast.utility

import scala.collection.mutable
import scala.language.postfixOps
import viper.silver.ast._

/** Utility methods for quantified predicate permissions. */
object QuantifiedPermissions {
  /** Utility methods for matching on quantified permission assertions.
    * Decomposes a quantifier of the shape (with quantified variables xs)
    *   `forall xs :: c(xs) ==> acc(...)``
    * into a tuple with the following elements:
    *   1. matched forall node
    *   2. condition `c(xs)``
    *   3. accessibility predicate `acc(...)`
    *
    * The implication is optional: if the quantifier body consists of an accessibility
    * predicate only, then the returned condition will be a [[TrueLit]].
    */
  object QuantifiedPermissionAssertion {
    def unapply(forall: Forall): Option[(Forall, Exp, AccessPredicate)] = {
      forall match {
        case SourceQuantifiedPermissionAssertion(`forall`, Implies(condition, res: AccessPredicate)) =>
          Some((forall, condition, res))
        case _ =>
          None
      }
    }
  }

  object SourceQuantifiedPermissionAssertion {
    def unapply(forall: Forall): Option[(Forall, Implies)] = {
      forall match {
        case Forall(_, _, implies: Implies) =>
          Some((forall, implies))
        case Forall(_, _, expr) =>
          Some(forall, Implies(BoolLit(true)(forall.pos, forall.info, forall.errT), expr)(forall.pos, forall.info, forall.errT))
        case _ =>
          None
      }
    }
  }

  /* TODO: Computing the set of quantified fields would probably benefit from caching
   *       the (sub)results per member. Ideally, it would be possible to retrieve the
   *       (cached) results from the members themselves,
   *       e.g. someMethod.quantifiedFields.
   */

  def quantifiedFields(root: Member, program: Program): collection.Set[Field] = {
    val collected = mutable.LinkedHashSet[Field]()
    val visited = mutable.Set[Member]()
    val toVisit = mutable.Queue[Member]()

    toVisit += root

    toVisit ++= Nodes.referencedMembers(root, program)

    quantifiedFields(toVisit, root, collected, visited, program)

    collected
  }

  /* TODO: See comment above about caching
   * TODO: Unify with corresponding code for fields
   */
  def quantifiedPredicates(root: Member, program: Program): collection.Set[Predicate] = {
    val collected = mutable.LinkedHashSet[Predicate]()
    val visited = mutable.Set[Member]()
    val toVisit = mutable.Queue[Member]()

    toVisit += root

    toVisit ++= Nodes.referencedMembers(root, program)

    quantifiedPredicates(toVisit, root, collected, visited, program)

    collected
  }

  def quantifiedMagicWands(root: Node, program: Program): collection.Set[MagicWandStructure.MagicWandStructure] = {
    (root collect {
      case QuantifiedPermissionAssertion(_, _, wand: MagicWand) => Seq(wand.structure(program))
      case Forall(_,triggers,_) => triggers flatMap (_.exps) collect {case wand: MagicWand => wand.structure(program)}
    } toSet) flatten
  }

  private def quantifiedFields(toVisit: mutable.Queue[Member],
                               root: Member,
                               collected: mutable.LinkedHashSet[Field],
                               visited: mutable.Set[Member],
                               program: Program): Unit = {

    while (toVisit.nonEmpty) {
      val currentRoot = toVisit.dequeue()

      val relevantNodes: Seq[Node] = currentRoot match {
        case m@Method(_, _, _, pres, posts, _) if m != root =>
          // use only specification of called methods
          pres ++ posts
        case f@Function(_, _, _, pres, posts, _) if false && f != root=>
          // use only specification of called functions
          pres ++ posts
        case _ => Seq(currentRoot)
      }

      visited += currentRoot

      for (n <- relevantNodes){
        n visit {
          case QuantifiedPermissionAssertion(_, _, acc: FieldAccessPredicate) =>
            collected += acc.loc.field
          case Forall(_,triggers,_) => collected ++= triggers flatMap (_.exps) collect {case fa: FieldAccess => fa.field}
        }
        utility.Nodes.referencedMembers(n, program) foreach (m =>
          if (!visited.contains(m)) toVisit += m)
      }
    }
  }

  private def quantifiedPredicates(toVisit: mutable.Queue[Member],
                                   root: Member,
                                   collected: mutable.LinkedHashSet[Predicate],
                                   visited: mutable.Set[Member],
                                   program: Program): Unit = {

    while (toVisit.nonEmpty) {
      val currentRoot = toVisit.dequeue()

      val relevantNodes: Seq[Node] = currentRoot match {
        case m@Method(_, _, _, pres, posts, _) if m != root =>
          // use only specification of called methods
          pres ++ posts
        case f@Function(_, _, _, pres, posts, _) if false && f != root =>
          // use only specification of called functions
          pres ++ posts
        case _ => Seq(currentRoot)
      }

      visited += currentRoot

      for (n <- relevantNodes){
        n visit {
          case QuantifiedPermissionAssertion(_, _, acc: PredicateAccessPredicate) =>
            collected += program.findPredicate(acc.loc.predicateName)
          case Forall(_,triggers,_) => collected ++= triggers flatMap (_.exps) collect {case pa: PredicateAccess => pa.loc(program)}
        }
        utility.Nodes.referencedMembers(n, program) foreach (m =>
          if (!visited.contains(m)) toVisit += m)

      }
    }
  }

  def desugarSourceQuantifiedPermissionSyntax(source: Forall): Seq[Forall] = {


    source match {
      case SourceQuantifiedPermissionAssertion(_, Implies(cond, rhs)) if (!rhs.isPure) =>
        /* Source forall denotes a quantified permission assertion that potentially
         * needs to be desugared
         */
        val vars = source.variables
        val triggers = source.triggers

        rhs match {
          case And(e0, e1) =>
            /* RHS is a conjunction; split into two foralls and recursively rewrite these */

            val foralls0 =
              desugarSourceQuantifiedPermissionSyntax(
                Forall(
                  vars,
                  triggers,
                  Implies(cond, e0)(rhs.pos, rhs.info)
                )(source.pos, source.info))

            val foralls1 =
              desugarSourceQuantifiedPermissionSyntax(
                Forall(
                  vars,
                  triggers,
                  Implies(cond, e1)(rhs.pos, rhs.info)
                )(source.pos, source.info))

            foralls0 ++ foralls1

          case Implies(e0, e1) =>
            /* RHS is an implication; pull its LHS out and rewrite the resulting forall */

            val newCond = And(cond, e0)(cond.pos, MakeInfoPair(cond.info,e0.info))

            val newForall =
              Forall(
                vars,
                triggers,
                Implies(newCond, e1)(rhs.pos, rhs.info) // TODO: this positional/info choice seems surprising. See also issue #249
              )(source.pos, source.info)

            desugarSourceQuantifiedPermissionSyntax(newForall)

          case CondExp(b, e0, e1) =>
            /* RHS is a conditional assertion; pull its condition out and rewrite the resulting foralls */

            val newCond0 = And(cond, b)(cond.pos, MakeInfoPair(cond.info,b.info))
            val newCond1 = And(cond, Not(b)(b.pos,b.info))(cond.pos, MakeInfoPair(cond.info,b.info))

            val newForalls0 =
              desugarSourceQuantifiedPermissionSyntax(Forall(
                vars,
                triggers,
                Implies(newCond0, e0)(rhs.pos, rhs.info) // TODO: this positional/info choice seems surprising. See also issue #249
              )(source.pos, source.info))

            val newForalls1 =
              desugarSourceQuantifiedPermissionSyntax(Forall(
                vars,
                triggers,
                Implies(newCond1, e1)(rhs.pos, rhs.info) // TODO: this positional/info choice seems surprising. See also issue #249
              )(source.pos, source.info))

                newForalls0 ++ newForalls1

          case nested@SourceQuantifiedPermissionAssertion(_, Implies(nestedCond, nestedRhs)) => // no need to check nestedRhs is pure, or else consistency check should already have failed (e.h. impure lhs of implication)
            /* Source forall denotes a quantified permission assertion that potentially
             * needs to be desugared
             */
            val nestedVars = nested.variables
            val nestedTriggers = if (nested.triggers.isEmpty) {
              val withTriggers = nested.autoTrigger
              withTriggers.triggers
            } else nested.triggers

            val combinedTriggers : Seq[Trigger] = // cross product
              triggers flatMap ((t:Trigger) => nestedTriggers map ((s:Trigger) => Trigger(t.exps ++ s.exps)(pos = t.pos, info = MakeInfoPair(t.info,s.info), errT = MakeTrafoPair(t.errT,s.errT))))

            val newCond = And(cond, nestedCond)(cond.pos, MakeInfoPair(cond.info,nestedCond.info))

            desugarSourceQuantifiedPermissionSyntax(Forall(vars ++ nestedVars, combinedTriggers, Implies(newCond, nestedRhs)(rhs.pos, rhs.info, rhs.errT))(source.pos,MakeInfoPair(source.info, nested.info),MakeTrafoPair(source.errT,nested.errT)))

          case lt@Let(v, e, bod) => {
            val forallWithoutLet = Forall(vars, triggers, Implies(cond, bod)(rhs.pos, rhs.info))(source.pos, source.info)
            // desugar the let-body
            val desugaredWithoutLet = desugarSourceQuantifiedPermissionSyntax(forallWithoutLet)
            desugaredWithoutLet.map{
              case SourceQuantifiedPermissionAssertion(iqp, Implies(icond, irhs)) if (!irhs.isPure) =>
                // Since the rhs cannot be a let-binding, we expand the let-expression
                Forall(iqp.variables, iqp.triggers.map(t => t.replace(v.localVar, e)), Implies(icond, irhs.replace(v.localVar, e))(iqp.pos, iqp.info))(iqp.pos, iqp.info)
              case iforall@Forall(ivars, itriggers, ibod) =>
                // For all pure parts of the quantifier, we just re-wrap the body into a let.
                Forall(ivars, itriggers, Let(v, e, ibod)(lt.pos, lt.info))(iforall.pos, iforall.info)
            }
          }
          case _ =>
            /* RHS does not need to be desugared (any further) */
            Seq(source)
        }

      case _ =>
        /* Source forall does not denote a quantified permission assertion;
         * no desugaring necessary
         */
        Seq(source)
    }
  }
}
