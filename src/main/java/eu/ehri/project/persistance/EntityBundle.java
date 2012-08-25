package eu.ehri.project.persistance;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import com.tinkerpop.frames.Property;

import eu.ehri.project.exceptions.ValidationError;
import eu.ehri.project.models.EntityTypes;
import eu.ehri.project.models.annotations.EntityType;

import org.apache.commons.collections.map.MultiValueMap;


public class EntityBundle <T> {
    private static final String GET = "get";
    private static final String MISSING_PROPERTY = "Missing mandatory field";
    private static final String EMPTY_VALUE = "No value given for mandatory field";
    private static final String INVALID_ENTITY = "No EntityType annotation";

    private final Map<String,Object> data;
    private final Class<T> cls;
    private MultiValueMap errors = new MultiValueMap();
    protected EntityBundle(Map<String,Object> data, Class<T> cls) {
        this.data = new HashMap<String,Object>(data);
        this.cls = cls;
    }
    
    private Map<String,Object> extendData() {
        Map<String,Object> ext = new HashMap<String,Object>(data);
        ext.put(EntityTypes.KEY, getEntityType());
        return ext;
    }
    
    public Boolean hasErrors() {
        return !errors.isEmpty();
    }
    
    public MultiValueMap getValidationErrors() {
        return errors;
    }
    
    public Map<String,Object> getData() throws ValidationError {
        return extendData();
    }
    
    public EntityBundle<T> setDataValue(String key, Object value) throws ValidationError {
        // FIXME: Seems like too much work being done here to maintain immutability???
        Map<String,Object> temp = new HashMap<String,Object>(data);
        temp.put(key, value);
        return new EntityBundle<T>(temp, cls);
    }
    
    public EntityBundle<T> setData(final Map<String, Object> data) {
        return new EntityBundle<T>(data, cls);
    }
    
    public Class<T> getBundleClass() {
        return cls;
    }
    
    public String getEntityType() {
        return cls.getAnnotation(EntityType.class).value();
    }
    
    protected void validate() throws ValidationError {
        checkFields();
        checkIsA();
        if (hasErrors())
            throw new ValidationError(cls, errors);
    }

    /**
     * @param data
     * @param cls
     * @param errors
     */
    private void checkFields() {
        for (Method method : cls.getMethods()) {
            for (Annotation annotation : method.getAnnotations()) {
                if (annotation instanceof Property && method.getName().startsWith(GET)) {
                    checkField(((Property) annotation).value(), method);
                }
            }
        }
    }
    
    private void checkField(String name, Method method) {
        if (!data.containsKey(name)) {
            errors.put(name, MISSING_PROPERTY);
        } else {
            Object value = data.get(name);
            if (value == null) {
                errors.put(name, EMPTY_VALUE);
            }
        }        
    }

    /**
     * @param data 
     * @param cls
     * @param errors
     */
    private void checkIsA() {
        EntityType annotation = cls.getAnnotation(EntityType.class);
        if (annotation == null) {
            errors.put("class", String.format("%s: '%s'", INVALID_ENTITY, cls.getName()));
        }
    }
    
}
