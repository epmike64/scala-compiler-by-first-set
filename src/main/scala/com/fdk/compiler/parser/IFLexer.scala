package com.fdk.compiler.parser

import com.fdk.compiler.parser.FToken.FTokenKind

trait IFLexer {

	def nextToken(): FToken
	
	def skip(n: Int): FToken

	def slide(kind: FTokenKind): FToken

	def lookAhead(n: Int): FToken
	
	def pushState(): Int
	
	def popState(stateId: Int, discard: Boolean): Unit
}
