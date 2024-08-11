package com.fdk.compiler.parser

import com.fdk.compiler.parser.FToken.FTokenKind

import scala.collection.mutable.ArrayBuffer


class Scanner(val tokenizer: FTokenizer) extends IFLexer {

	var token: FToken = null
	val savedTokens = ArrayBuffer[FToken]()

	def currentToken(): FToken = lookAhead(0)

	override def lookAhead(lookahead: Int): FToken = {
		if (lookahead == 0) token

		else {
			ensureLookahead(lookahead)
			savedTokens(lookahead - 1)
		}
	}
	
	private def ensureLookahead(lookahead: Int): Unit = {
		for (i <- savedTokens.size until lookahead) {
			savedTokens :+ tokenizer.readToken()
		}
	}

	override def nextToken(): FToken = {
		if (!savedTokens.isEmpty) token = savedTokens.remove(0)
		else token = tokenizer.readToken()
		token
	}

	override def skip(n: Int): FToken = {
		for (_ <- 0 until n) {
			token = nextToken()
		}
		token
	}

	override def slide(kind: FTokenKind): FToken = {
		while (token.kind == kind) {
			token = nextToken()
		}
		token
	}
}
