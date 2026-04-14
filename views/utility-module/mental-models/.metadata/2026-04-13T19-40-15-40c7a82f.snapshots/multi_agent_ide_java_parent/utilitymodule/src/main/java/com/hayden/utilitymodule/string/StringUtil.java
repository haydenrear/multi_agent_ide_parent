package com.hayden.utilitymodule.string;

import com.hayden.utilitymodule.result.Result;
import com.hayden.utilitymodule.result.error.SingleError;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.*;

public interface StringUtil {


    static Result<String, SingleError> getSubstringByByteLength(String input, int maxByteLength, Charset charset) {
        CharsetEncoder encoder = charset.newEncoder();
        ByteBuffer limitedSizeOutput = ByteBuffer.allocate(Math.min(maxByteLength, input.getBytes().length));
        CoderResult coderResult = encoder.encode(CharBuffer.wrap(input.toCharArray()), limitedSizeOutput, true);
        if (coderResult.isError()) {
            try {
                coderResult.throwException();
            } catch (CharacterCodingException e) {
                return Result.err(SingleError.fromE(e));
            }
        }

        limitedSizeOutput.flip();
        byte[] byteArray = new byte[limitedSizeOutput.remaining()];
        limitedSizeOutput.get(byteArray);
        // Return the substring created from the byte array up to the maxByteLength
        return Result.ok(new String(byteArray, charset));
    }


}
