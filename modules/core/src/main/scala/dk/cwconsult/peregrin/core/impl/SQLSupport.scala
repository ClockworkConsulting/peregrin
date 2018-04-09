package dk.cwconsult.peregrin.core.impl

private[core] object SQLSupport {

  private[this] val dblQuote: String = "\""
  private[this] val dblDblQuote: String = dblQuote + dblQuote

  def quoteIdentifier(s: String): String =
    dblQuote + s.replace(dblQuote, dblDblQuote) + dblQuote

}
