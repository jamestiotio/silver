package silAST.types

import silAST.domains._
import silAST.source.noLocation
import silAST.expressions.util.TermSequence

object referenceDomain extends Domain {
  override val name = "Ref"
  override val fullName : String = name
  override val sourceLocation = noLocation

  override def functions = Set[DomainFunction](nullFunction,referenceEquality)
  override def predicates = Set[DomainPredicate]()
  override def axioms = Set.empty[DomainAxiom]
  override def substitute(ts:TypeVariableSubstitution) = this
  override def getType = referenceType
  override def freeTypeVariables = Set()
  override def isCompatible(other : Domain) = other == this
}


/////////////////////////////////////////////////////////////////////
/////////////////////////////////////////////////////////////////////
object referenceType extends ReferenceDataType
{
  override val toString = "ref"
  override def isCompatible(other : DataType) = other eq referenceType
}

///////////////////////////////////////////////////////////////////////////
object nullFunction extends DomainFunction
{
  override val sourceLocation = noLocation
  override val name = "null"
  override val signature = new DomainFunctionSignature(noLocation,DataTypeSequence(),referenceType)
  override lazy val domain = referenceDomain

  override def toString(ts : TermSequence) = name

  override def substitute(ts:TypeVariableSubstitution) = this

}

///////////////////////////////////////////////////////////////////////////
object referenceEquality extends DomainFunction
{
  override val sourceLocation = noLocation
  override val name = "==<ref>"
  override val signature = new DomainFunctionSignature(noLocation,DataTypeSequence(referenceType,referenceType),booleanType)
  override lazy val domain = referenceDomain

  override def toString(ts : TermSequence) = 
  {
    require(ts.size == 2)
    ts(0) + "==" + ts(1)
  }

  override def substitute(ts:TypeVariableSubstitution) = this

}