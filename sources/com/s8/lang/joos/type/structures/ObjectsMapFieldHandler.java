package com.s8.lang.joos.type.structures;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import com.s8.lang.joos.JOOS_Context;
import com.s8.lang.joos.JOOS_Type;
import com.s8.lang.joos.composing.ComposingScope;
import com.s8.lang.joos.composing.JOOS_ComposingException;
import com.s8.lang.joos.parsing.JOOS_ParsingException;
import com.s8.lang.joos.parsing.MappedScope;
import com.s8.lang.joos.parsing.ObjectScope;
import com.s8.lang.joos.parsing.ParsingScope;
import com.s8.lang.joos.type.FieldHandler;
import com.s8.lang.joos.type.JOOS_CompilingException;
import com.s8.lang.joos.type.TypeHandler;

public class ObjectsMapFieldHandler extends FieldHandler {

	/**
	 * 
	 */
	public Class<?> valueType;

	public ObjectsMapFieldHandler(String name, Field field) throws JOOS_CompilingException {
		super(name, field);


		Type[] typeVars = ((ParameterizedType) field.getGenericType()).getActualTypeArguments();


		Type key = typeVars[0];
		if(!key.equals(String.class)) {
			throw new JOOS_CompilingException(field.getType(), "Only String are accetped as keys");
		}

		Type actualValueType = typeVars[1];

		// if type is like: MySubObject<T>
		if(actualValueType instanceof ParameterizedType) {
			valueType = (Class<?>) ((ParameterizedType) actualValueType).getRawType();
		}
		// if type is simply like: MySubObject
		else if(actualValueType instanceof Class<?>){
			valueType = (Class<?>) actualValueType;
		}
	}

	public void set(Object object, Object child) throws IllegalArgumentException, IllegalAccessException {
		field.set(object, child);
	}

	@Override
	public Class<?> getSubType() {
		return valueType;
	}



	public Object get(Object object) throws IllegalArgumentException, IllegalAccessException {
		return field.get(object);
	}

	@Override
	public void subDiscover(JOOS_Context context) throws JOOS_CompilingException {
		if(valueType!=null && valueType.getAnnotation(JOOS_Type.class)!=null) {
			context.discover(valueType);	
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public boolean compose(Object object, ComposingScope scope) throws JOOS_ComposingException, IOException {


		// retrieve array
		Map<String, Object> map = null;
		try {
			map = (Map<String, Object>) field.get(object);
		} 
		catch (IllegalArgumentException | IllegalAccessException e) {
			throw new JOOS_ComposingException(e.getMessage());
		}


		if(map!=null) {

			// field description
			scope.newItem();
			scope.append(name);
			scope.append(':');

			ComposingScope enclosedScope = scope.enterSubscope('{', '}', true);

			enclosedScope.open();
			Object value;
			for(Entry<String, Object> entry : map.entrySet()) {

				enclosedScope.newItem();
				scope.append(entry.getKey());
				scope.append(": ");
				
				value = entry.getValue();
				TypeHandler typeHandler = enclosedScope.getTypeHandler(value);
				typeHandler.compose(value, enclosedScope);				
			};
			enclosedScope.close();
			return true;
		}
		else {
			return false;
		}
	}

	@Override
	public ParsingScope openScope(Object object) {
		final HashMap<String, Object> map = new HashMap<String, Object>();
		return new MappedScope() {

			@Override
			public ParsingScope openEntry(String declarator) throws JOOS_ParsingException {
				return new ObjectScope(new OnParsedObject() {
					public @Override void set(Object value) throws JOOS_ParsingException {
						map.put(declarator, value);
					}
				});
			}

			@Override
			public boolean isDefinable() {
				return false;
			}

			@Override
			public void define(String definition, JOOS_Context context) throws InstantiationException,
			IllegalAccessException, IllegalArgumentException, InvocationTargetException {
				// nothing to define
			}

			@Override
			public void close() throws JOOS_ParsingException {
				try {
					ObjectsMapFieldHandler.this.set(object, map);
				}
				catch (IllegalArgumentException | IllegalAccessException e) {
					throw new JOOS_ParsingException("Failed to set object due to "+e.getMessage());
				}
			}
		};
	}
}
