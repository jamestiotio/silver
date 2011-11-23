package silAST.symbols
import silAST.ASTNode
import silAST.source.SourceLocation
import silAST.expressions.terms.GTerm

abstract class ArgumentSequence[+T <: GTerm[T]]( sl : SourceLocation, private val args : Seq[T]) extends ASTNode(sl){
	def asSeq() : Seq[T] = 
	{
		return args
	}
	
	override def subNodes() : Seq[T] = { return args}
}