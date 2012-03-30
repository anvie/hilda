package com.ansvia.hilda
import java.util.regex.Pattern

trait Beautifier {
	protected def beautifyContent(content: String): String = {
        val data = Pattern.compile("\n").split(content)
                .map(z => z.trim())
                .filter(_.length() > 0)
                .reduceLeft(_ + "\n" + _)
		data + "\n"
	}
}

