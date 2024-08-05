package com.fdk.compiler.parser

import com.fdk.compiler.FToken
import com.fdk.compiler.parser.FTokenKind.*
import com.fdk.compiler.tree.{FExpression, FImport, FModifiers, FPackageDecl, FTree, FTreeMaker}
import com.fdk.compiler.util.FName

import scala.collection.mutable.ArrayBuffer

class FParser(lexer: IFLexer) extends IFParser {
	{
		lexer.nextToken()
	}
	private[this] var token: FToken = lexer.token
	private[this] val F: FTreeMaker = new FTreeMaker()
	private[this] val endPosTable: scala.collection.mutable.Map[FTree, Int] = scala.collection.mutable.Map()

	def toP[T <: FTree](t: T): T = {t}
	
	def next(): Unit = {
		token = lexer.next
	}
	
	def skip(n: Int): Unit = {
		if(n == 1) next()
		else if (n > 1) token = lexer.skip(n)
		else throw new IllegalArgumentException("n must be positive")
	}
	
	def lookAhead(n: Int): FToken = {
		if(n == 0) token
		else if(n > 0) lexer.lookAhead(n)
		else throw new IllegalArgumentException("n must be positive")
	}
	/** If next input token matches given token, skip it, otherwise report
	 * an error.
	 */
	def accept(tk: FTokenKind): Unit = {
		if(token.kind == tk) next()
		else {
			setErrorEndPos(token.pos)
			reportSyntaxError(token.pos, "expected", tk)
		}
	}
	
	def isTokenLa(kinds: FTokenKind*): Boolean = {
		for(i <- 0 until kinds.length){
			if(lexer.lookAhead(i).kind != kinds(i)) return false
		}
		true
	}
	

	def isTokenLaOneOf(n: Int, kinds: FTokenKind*): Boolean = {
		kinds.contains(lookAhead(n))
	}
	
	def isToken(kind: FTokenKind): Boolean = {
		token.kind == kind
	}

	def isTokenOneOf(kinds: FTokenKind*): Boolean = {
		kinds.contains(token.kind)
	}
	
	def setErrorEndPos(errPos: Int): Unit = {
		//endPosTable.setErrorEndPos(errPos)
	}

	def reportSyntaxError(pos: Int, key: String, kinds: FTokenKind*): Unit = {
		//reporter.syntaxError(token.offset, msg)
	}

	def ident(): FName = {
		val name = token.name
		accept(IDENTIFIER)
		name
	}

	/**
	 * Qualident = Ident { DOT [Annotations] Ident }
	 */
	def qualId(): FExpression = {
		var t: FExpression = toP(F.at(token.pos).ident(ident()))
		while (token.kind == DOT) {
			val pos = token.pos
			next()
			t = toP(F.at(token.pos).select(t, ident()))
		}
		t
	}

	def packageDecl(): FPackageDecl = {
		val startPos = token.pos
		next()
		val pid = qualId()
		val pd = F.at(startPos).packageDecl(pid)
		endPosTable(pd) = token.pos
		pd
	}

	def importDecl(): FImport = {
		val startPos = token.pos
		next()
		val id = qualId()
		val imp = F.at(startPos).makeImport(id)
		endPosTable(imp) = token.pos
		imp
	}

	/*
		paramType:   type_  | '=>' type_  | type_ '*'
	 */
	def paramType(): Unit = {
		token.kind match {
			case FAT_ARROW => {
				next()
				_type()
			}
			case _ => {
				_type()
				next()
				token.kind match {
					case STAR => next()
				}
			}
		}
	}

	def infixType(): Unit = {
		token.kind match {
			case LPAREN => {
				next()
				_type()
				while (token.kind != RPAREN) {
					_type()
					if (token.kind == COMMA) {
						next()
						_type()
					}
				}
				accept(RPAREN)
			}
			case _ => {
				qualId()

			}
		}
	}

	/*
		type_ : functionArgTypes '=>' type_ | infixType existentialClause?
	 */
	def _type(): Unit = {
		token.kind match {
			case LPAREN =>
				next()
				while (token.kind != RPAREN) {
					paramType()
					if (token.kind == COMMA) {
						next()
					}
				}
				accept(RPAREN)
				if (token.kind == FAT_ARROW) {
					next()
					_type()
				}
			case _ => infixType()
		}
	}

	def typeParam(): Unit = {
		token.kind match {
			case IDENTIFIER => ident()
			case UNDERSCORE => next()
		}
		if (token.kind == LBRACKET) {
			next()
			if (token.kind != RBRACKET) {
				variantTypeParam()
				while (token.kind == COMMA) {
					next()
					variantTypeParam()
				}
			}
			accept(RBRACKET)
		}
		if (token.kind == LOWER_BOUND) {
			next()
			_type()
		}
		if (token.kind == UPPER_BOUND) {
			next()
			_type()
		}
		if (token.kind == COLON) {
			next() //Context bound
			_type()
		}
	}

	def classQualifier(): Unit = {
		accept(LBRACKET)
		ident()
		accept(RBRACKET)
	}
	
	def stableId2(): Unit = {
		if (token.kind == LBRACKET) {
			classQualifier()
		}
		accept(DOT)
		accept(IDENTIFIER)
	}

	def stableIdRest(): Unit = {
		while (token.kind == DOT && isTokenLaOneOf(1, IDENTIFIER)) {
			next()
			ident()
		}
	}
	
	def stableId(): Unit = {
		if(isToken(IDENTIFIER)){
			ident()
			if (token.kind == DOT) {
				if(isTokenLaOneOf(1, THIS, SUPER)){
					skip(2)
					stableId2()
				} 
			}
			stableIdRest()
			
		} else if(isTokenOneOf(THIS, SUPER)){
			next()
			stableId2()
			stableIdRest()
		} else {
			reportSyntaxError(token.pos, "expected", IDENTIFIER, THIS, SUPER)
		}
	}

	def variantTypeParam(): Unit = {
		token.kind match {
			case PLUS | SUB => next()
		}
		typeParam()
	}

	def typeParamClause(): Option[FTree] = {
		accept(LBRACKET)
		variantTypeParam()
		while (token.kind == COMMA) {
			next()
			variantTypeParam()
		}
		accept(RBRACKET)
		None
	}

	def pattern(): Unit = {
		//pattern1()
	}
	def patterns(): Unit = {
		if (token.kind == UNDERSCORE) {
			next()
			accept(STAR)
		}
		else {
			pattern()
			while (token.kind == COMMA) {
				next()
				patterns()
			}
		}
	}

	/*
	simplePattern
				: '_'
				| Varid
				| literal
				| stableId ('(' patterns? ')')?
				| stableId '(' (patterns ',')? (Id '@')? '_' '*' ')'
				| '(' patterns? ')'
	 */
	def simplePattern(): Unit = {
		token.kind match {
			case UNDERSCORE => next()
			case LITERAL => next()
			case IDENTIFIER => {
				stableId()
				if (token.kind == LPAREN) {
					next()
					if (token.kind != RPAREN) {
						patterns()
					}
					accept(RPAREN)
				}
			}
		}
	}

	/*
		enumerators : generator+
		 generator: pattern1 '<-' expr (guard_ | pattern1 '=' expr)*
		 pattern1: (BoundVarid | '_' | Id) ':' typePat | pattern2
		 pattern2: Id ('@' pattern3)?| pattern3
		 pattern3: simplePattern| simplePattern (Id NL? simplePattern)*
		 simplePattern
						 : '_'
						 | Varid
						 | literal
						 | stableId ('(' patterns? ')')?
						 | stableId '(' (patterns ',')? (Id '@')? '_' '*' ')'
						 | '(' patterns? ')'
						 
	 */
	def generator(): Unit = {
		token.kind match {
			case IDENTIFIER => {
				ident()
				token.kind match {
					case COLON => {
						next()
						_type()
					}
					case AT => {
						next()
						simplePattern()
					}
					case _ => simplePattern()
				}
			}
			case UNDERSCORE /*|BoundVarid*/ => {
				next()
				accept(COLON)
				_type()
			}
		}
	}

	def enumerators(): Unit = {
		while (token.kind != RPAREN || token.kind != RBRACE) {
			generator()
		}
	}

	/*
	expr1
			 : 'if' '(' expr ')' NL* expr ('else' expr)?
			 | 'while' '(' expr ')' NL* expr
			 | 'try' expr ('catch' expr)? ('finally' expr)?
			 | 'do' expr 'while' '(' expr ')'
			 | 'for' ('(' enumerators ')' | '{' enumerators '}') 'yield'? expr
			 | 'throw' expr
			 | 'return' expr?
			 | ((simpleExpr | simpleExpr1 '_'?) '.')? Id '=' expr
			 | simpleExpr1 argumentExprs '=' expr
			 | postfixExpr ascription?
			 | postfixExpr 'match' '{' caseClauses '}'
	 */
	def expr1(): Unit = {
		token.kind match {
			case IF => {
				accept(LPAREN)
				expr()
				accept(RBRACE)
				expr()
				if (token.kind == ELSE) {
					next()
					expr()
				}
			}
			case WHILE => {
				accept(LPAREN)
				expr()
				accept(RBRACE)
				expr()
			}
			case TRY => {
				expr()
				if (token.kind == CATCH) {
					next()
					expr()
				}
				if (token.kind == FINALLY) {
					next()
					expr()
				}
			}
			case DO => {
				expr()
				accept(WHILE)
				accept(LPAREN)
				expr()
				accept(RPAREN)
			}
			case FOR => {
				if (token.kind == LPAREN) {
					next()
					enumerators()
					accept(RPAREN)
				}
				else {
					accept(LBRACE)
					enumerators()
					accept(RBRACE)
				}
				if (token.kind == YIELD) {
					next()
				}
				expr()
			}
			case THROW => expr()
			case RETURN => ???
		}
	}
	/*  
		expr: (bindings | 'implicit'? Id | '_') '=>' expr | expr1
			bindings: '(' binding (',' binding)* ')'
				 binding: (Id | '_') (':' type_)?
		expr1:
			 'if' '(' expr ')' NL* expr ('else' expr)?
			 | 'while' '(' expr ')' NL* expr
			 | 'try' expr ('catch' expr)? ('finally' expr)?
			 | 'do' expr 'while' '(' expr ')'
			 | 'for' ('(' enumerators ')' | '{' enumerators '}') 'yield'? expr
			 | 'throw' expr
			 | 'return' expr?
			 | ((simpleExpr | simpleExpr1 '_'?) '.')? Id '=' expr
			 | simpleExpr1 argumentExprs '=' expr
			 | postfixExpr ascription?
			 | postfixExpr 'match' '{' caseClauses '}'
	*/

	def expr(): Unit = {
		while (token.kind == IDENTIFIER || token.kind == UNDERSCORE) {
			next()
			if (token.kind == COLON) {
				next()
				_type()
			}
		}
		if (token.kind == FAT_ARROW) {
			next()
			expr()
		}
		else {
			expr1()
		}
	}

	/*
		classParam: annotation* modifier* ('val' | 'var')? Id ':' paramType ('=' expr)?
	 */
	def classParam(): Unit = {
		val mods = modifiersOpt()
		token.kind match {
			case VAR | VAL => next()
		}
		ident()
		accept(COLON)
		paramType()
		if (token.kind == EQ) {
			next()
			expr()
		}
	}

	/**
	 * ClassDef ::= id [TypeParamClause] {Annotation} [AccessModifier] ClassParamClauses classTemplateOpt
	 */
	def classDef(isCase: Boolean): FTree = {
		val startPos = token.pos
		accept(CLASS)
		val name = ident()
		/*
		typeParamClause: '[' variantTypeParam (',' variantTypeParam)* ']'
		 */
		if (token.kind == LBRACKET) {
			next()
			variantTypeParam()
			while (token.kind == COMMA) {
				next()
				variantTypeParam()
			}
			accept(RBRACKET)
		}
		/*
		accessModifier: ('private' | 'protected') accessQualifier?
		 */
		token.kind match {
			case PRIVATE | PROTECTED =>
				next()
				if (token.kind == LBRACKET) {
					accessQualifier()
				}
		}
		/*
			classParamClauses: classParamClause* (NL? '(' 'implicit' classParams ')')?
			classParamClause: NL? '(' classParams? ')'
			classParams: classParam (',' classParam)*
			classParam: annotation* modifier* ('val' | 'var')? Id ':' paramType ('=' expr)?
		 */
		if (token.kind == LPAREN) {
			next()
			while (token.kind != RPAREN) {
				classParam()
				if (token.kind == COMMA) {
					next()
					classParam()
				}
			}
			accept(RPAREN)
		}

		if (token.kind == EXTENDS) {
			next()
			if (token.kind == LBRACE) {
				next()
				//earlyDefs()
				accept(RBRACE)
				accept(WITH)
			}
		}
		if (token.kind == LPAREN) { //templateBody
			next()

			accept(RPAREN)
		}

		val cd = F.at(startPos).makeClassDecl()
		endPosTable(cd) = token.pos
		cd
	}

	def objectDef(isCase: Boolean): FTree = {
		val startPos = token.pos
		accept(OBJECT)
		val name = ident()
		val od = F.at(startPos).makeClassDecl()
		endPosTable(od) = token.pos
		od
	}

	def traitDef(): FTree = {
		val startPos = token.pos
		accept(TRAIT)
		val name = ident()
		val td = F.at(startPos).makeTraitDecl()
		endPosTable(td) = token.pos
		td
	}

	def accessQualifier(): Unit = {
		accept(LBRACKET)
		token.kind match {
			case IDENTIFIER => ident()
			case THIS => next()
		}
		accept(RBRACKET)
	}

	def modifier(): Option[Int] = {

		token.kind match

			case ABSTRACT | FINAL | SEALED | IMPLICIT | LAZY | OVERRIDE =>
				next()
				Some(1)

			case PRIVATE | PROTECTED =>
				next()
				if (token.kind == LBRACKET) {
					accessQualifier()
				}
				Some(2)

			case _ =>
				None
	}

	def modifiersOpt(): FModifiers = {
		val mods = new FModifiers()
		while (token.kind != EOF) {
			val mod: Option[Int] = modifier()
			mod match {
				case Some(n) => ??? // mods.addFlag(FModifiers.ABSTRACT)
				case _ => return mods
			}
		}
		mods
	}


	def tmplDef(mods: FModifiers): FTree = {
		token.kind match {
			case CASE =>
				next()
				token.kind match {
					case CLASS => classDef(true)
					case OBJECT => objectDef(true)
					case _ => {
						reportSyntaxError(token.pos, "expected", CLASS)
						F.makeSkip()
					}
				}
			case CLASS => classDef(false)
			case OBJECT => objectDef(false)
			case TRAIT => traitDef()
			case _ => {
				reportSyntaxError(token.pos, "expected", CLASS)
				F.makeSkip()
			}
		}
	}

	def topStatement(): FTree = {
		token.kind match {
			case IMPORT => importDecl()
			case _ =>
				val mods = modifiersOpt()
				tmplDef(mods)
		}
	}

	def topStatementSeq(): ArrayBuffer[FTree] = {
		val defs: ArrayBuffer[FTree] = ArrayBuffer()
		while (token.kind != EOF) {
			defs += topStatement()
		}
		defs
	}

	def compilationUnit(): ArrayBuffer[FTree] = {

		val defs: ArrayBuffer[FTree] = ArrayBuffer()

		while (token.kind == PACKAGE) {
			defs += packageDecl()
		}

		defs ++= topStatementSeq()
		defs
	}
}

