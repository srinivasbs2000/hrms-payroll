package com.acme.hrms.payroll.statutory;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.deser.std.StdScalarDeserializer;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.ser.std.StdScalarSerializer;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.math.BigDecimal;
import java.util.regex.Pattern;

@Documented
@Target({
  ElementType.FIELD,
  ElementType.METHOD,
  ElementType.PARAMETER,
  ElementType.RECORD_COMPONENT
})
@Retention(RetentionPolicy.RUNTIME)
@JacksonAnnotationsInside
@JsonSerialize(using = DecimalString.Serializer.class)
@JsonDeserialize(using = DecimalString.Deserializer.class)
public @interface DecimalString {
  Pattern VALUE_PATTERN = Pattern.compile(
      "^-?(?:0|[1-9][0-9]{0,14})(?:\\.[0-9]{1,4})?$");

  final class Serializer extends StdScalarSerializer<BigDecimal> {
    public Serializer() {
      super(BigDecimal.class);
    }

    @Override
    public void serialize(
        BigDecimal value,
        JsonGenerator generator,
        SerializerProvider provider) throws IOException {
      generator.writeString(value.toPlainString());
    }
  }

  final class Deserializer extends StdScalarDeserializer<BigDecimal> {
    public Deserializer() {
      super(BigDecimal.class);
    }

    @Override
    public BigDecimal deserialize(
        JsonParser parser,
        DeserializationContext context) throws IOException {
      if (!parser.hasToken(JsonToken.VALUE_STRING)) {
        throw MismatchedInputException.from(
            parser,
            BigDecimal.class,
            "Monetary values must be JSON strings");
      }

      String value = parser.getText();
      if (!VALUE_PATTERN.matcher(value).matches()) {
        throw InvalidFormatException.from(
            parser,
            "Monetary string must contain at most 15 integer and 4 fraction digits",
            value,
            BigDecimal.class);
      }

      return new BigDecimal(value);
    }
  }
}
