package silAST.expressions.terms

import silAST.programs.NodeFactory
import silAST.source.SourceLocation
import silAST.symbols.logical.quantification.BoundVariable
import collection.mutable.HashSet
import silAST.domains.DomainFunction
import silAST.types.{DataTypeFactory, DataType}
import silAST.expressions.util.{DTermSequence, GTermSequence}


trait DTermFactory extends NodeFactory with GTermFactory with DataTypeFactory {

  def validBoundVariableName(name:String) : Boolean =
    name!="this"

  /////////////////////////////////////////////////////////////////////////
  def makeBoundVariable(sl: SourceLocation, name: String, dataType: DataType): BoundVariable = {
    require(dataTypes contains dataType)
    require(validBoundVariableName(name))
    val result: BoundVariable = new BoundVariable(sl, name, dataType)
    boundVariables += result
    result
  }

  /////////////////////////////////////////////////////////////////////////
  def makeBoundVariableTerm(sl: SourceLocation, v: BoundVariable): BoundVariableTerm = {
    require(boundVariables contains v)
    addTerm(new BoundVariableTerm(sl, v))
  }

  /////////////////////////////////////////////////////////////////////////
  def makeDDomainFunctionApplicationTerm(sl: SourceLocation, f: DomainFunction, a: DTermSequence): DDomainFunctionApplicationTerm = {
    require(a != null)
    require(a.forall(_ != null))
    require(a.forall(terms contains _))
    require(domainFunctions contains f)

    a match {
      case a: GTermSequence => makeGDomainFunctionApplicationTerm(sl, f, a)
      case _ => addTerm(new DDomainFunctionApplicationTermC(sl, f, a))
    }
  }

  /////////////////////////////////////////////////////////////////////////
  protected[silAST] val boundVariables = new HashSet[BoundVariable]
}