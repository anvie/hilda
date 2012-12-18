package com.ansvia.hilda
import java.util.regex.Pattern

trait Beautifier {
	protected def beautifyContent(content: String): String = {
        val datal = Pattern.compile("\n").split(content)
                .map(z => z.trim())
                .filter(_.length() > 0)

        val data =
            if (datal.length > 0){
                datal.reduceLeft(_ + "\n" + _)
            }else{
                datal.foldLeft("")(_ + _)
            }

		data + "\n"
	}
}

