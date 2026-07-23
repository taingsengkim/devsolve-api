package co.istad.ite.devsoleapi.feature.comment;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class CommentableTypeConverter
        implements AttributeConverter<CommentableType, String> {

    @Override
    public String convertToDatabaseColumn(CommentableType attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.getValue();
    }

    @Override
    public CommentableType convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        return CommentableType.fromValue(dbData);
    }
}
