package com.github.danfickle.cpptojavasourceconverter;

import java.util.Map;

import org.eclipse.cdt.core.dom.ast.*;
import org.eclipse.cdt.core.dom.ast.cpp.*;
import org.eclipse.cdt.internal.core.dom.parser.cpp.ICPPUnknownType;

class TypeManager 
{
	private final TranslationUnitContext ctx;
	
	TypeManager(TranslationUnitContext con)
	{
		ctx = con;
	}
	
	enum TypeEnum
	{
		NUMBER, 
		BOOLEAN,
		CHAR,
		VOID,
		OBJECT_POINTER,
		BASIC_POINTER,
		OBJECT,
		OBJECT_ARRAY,
		BASIC_ARRAY,
		ANY,
		BASIC_REFERENCE,
		OBJECT_REFERENCE,
		OTHER,
		ENUMERATION,
		UNKNOWN,
		FUNCTION,
		FUNCTION_POINTER,
		FUNCTION_ARRAY,
		FUNCTION_REFERENCE, VOID_POINTER;
	}
	
	static boolean isOneOf(IType tp, TypeEnum... tps) throws DOMException
	{
		TypeEnum temp = getTypeEnum(tp);
		
		for (TypeEnum type : tps)
		{
			if (type == temp)
				return true;
		}

		return false;
	}
	
	
	static boolean isBasicType(IType tp) throws DOMException
	{
		return isOneOf(tp, TypeEnum.BOOLEAN, TypeEnum.CHAR, TypeEnum.NUMBER);
	}
	
	static IType expand(IType type)
	{
		while (type instanceof ITypedef ||
			   type instanceof IQualifierType)
		{
			if (type instanceof ITypedef)
				type = ((ITypedef) type).getType();
			else
				type = ((IQualifierType) type).getType();
		}		
		
		return type;
	}
	
	/**
	 * Determines if a type will turn into a pointer.
	 * @throws DOMException 
	 */
	static boolean isPtrOrArrayBasic(IType type) throws DOMException
	{
		return isOneOf(type, TypeEnum.BASIC_POINTER, TypeEnum.BASIC_ARRAY);
	}
	
	enum TypeType
	{
		INTERFACE,
		IMPLEMENTATION,
		RAW;
	}
	
	/**
	 * Attempts to convert a CDT type to the approriate Java
	 * type.
	 */
	String cppToJavaType(IType type, TypeType tp) throws DOMException
	{
		// Check that it is not an anonymous type.
		for (Map.Entry<IType, String> ent : ctx.anonTypes.entrySet())
		{
			if (ent.getKey().isSameType(type))
				return ent.getValue();
		}
		
		if (type instanceof IBasicType)
		{
			// Primitive type - int, bool, char, etc...
			IBasicType basic = (IBasicType) type;
			
			if (tp == TypeType.RAW)
				return evaluateSimpleType(basic.getKind(), basic.isShort(), basic.isLongLong(), basic.isUnsigned());
			else if (tp == TypeType.INTERFACE)
				return "I" + evaluateSimpleTypeBoxed(basic.getKind(), basic.isShort(), basic.isLongLong(), basic.isUnsigned());
			else
				return "M" + evaluateSimpleTypeBoxed(basic.getKind(), basic.isShort(), basic.isLongLong(), basic.isUnsigned());
		}
		else if (type instanceof IArrayType)
		{
			IArrayType array = (IArrayType) type;

			String jt = cppToJavaType(array.getType(), tp);
			
			if (tp == TypeType.RAW)
				return jt;
			else if (tp == TypeType.INTERFACE)
				return jt;
			else
				return jt + "Multi";
		}
		else if (type instanceof ICompositeType)
		{
			ICompositeType comp = (ICompositeType) type;
			String simple = getSimpleType(comp.getName());

			//printerr(comp.getClass().getCanonicalName());
			
//			if (type instanceof ICPPTemplateInstance)
//			{
//				ICPPTemplateInstance template = (ICPPTemplateInstance) type;
//				print("template instance");
//
//				ParameterizedType param = ast.newParameterizedType(simple);
//				List<Type> list = getTypeParams(template.getTemplateArguments());
//				param.typeArguments().addAll(list);
//				return param;
//			}
//			else
			{
				return simple;
			}
		}
		else if (type instanceof IPointerType)
		{
			IType baseType = getPointerBaseType(type);
			int ptrCount = getPointerIndirectionCount(type);

			if (isBasicType(baseType) && ptrCount == 1)
			{
				return cppToJavaType(baseType, tp);
			}
			else
			{
				// One level of indirection becomes:
				//   IPtrObject<BASE_TYPE>
				// Two levels of indirection become:
				//   IPtrObject<IPtrObject<BASE_TYPE>>
				// and so on.
				String wrap = cppToJavaType(baseType, tp);
				
				if (isBasicType(baseType))
					ptrCount--;
				
				while (ptrCount-- > 0)
				{
					if (tp == TypeType.INTERFACE)
						wrap = "IPtrObject<" + wrap + ">";
					else
						wrap = "PtrObject<" + wrap + ">";
				}

				return wrap;
			}
		}
		else if (type instanceof ICPPReferenceType)
		{
			ICPPReferenceType ref = (ICPPReferenceType) type;

			if ((ref.getType() instanceof IQualifierType) || 
				ref.getType() instanceof ICPPClassType /* &&
				 ((IQualifierType) ref.getType()).isConst()) */)
			{
				return cppToJavaType(ref.getType(), tp);
			}
			else if (ref.getType() instanceof IBasicType || 
					(ref.getType() instanceof ITypedef && 
					((ITypedef) ref.getType()).getType() instanceof IBasicType))
			{
				IBasicType basic;
				if (ref.getType() instanceof ITypedef)
					basic = ((IBasicType)((ITypedef) ref.getType()).getType());
				else
					basic = (IBasicType) ref.getType();

				String basicStr = evaluateSimpleTypeBoxed(basic.getKind(), basic.isShort(), basic.isLongLong(), basic.isUnsigned());
				String simpleType = "I" + basicStr;
				return simpleType;
			}
//			else
//			{
//				ParameterizedType paramType = ast.newParameterizedType(jast.newType("Ref"));    		  
//				paramType.typeArguments().add(cppToJavaType(ref.getType(), false, true));  		  
//				return paramType;
//			}
		}
		else if (type instanceof IQualifierType)
		{
			IQualifierType qual = (IQualifierType) type;
			return cppToJavaType(qual.getType(), tp);
		}
		else if (type instanceof IProblemBinding)
		{
			IProblemBinding prob = (IProblemBinding) type;
			MyLogger.logImportant("PROBLEM:" + prob.getMessage() + prob.getFileName() + prob.getLineNumber());

			return "PROBLEM__";
		}
		else if (type instanceof ITypedef)
		{
			ITypedef typedef = (ITypedef) type;
			return cppToJavaType(typedef.getType(), tp);
		}
		else if (type instanceof IEnumeration)
		{
			IEnumeration enumeration = (IEnumeration) type;
			return getSimpleType(enumeration.getName());
		}
		else if (type instanceof IFunctionType)
		{
			//IFunctionType func = (IFunctionType) type;
			return "FunctionPointer";
		}
		else if (type instanceof IProblemType)
		{
			IProblemType prob = (IProblemType)type; 
			MyLogger.logImportant("Problem type: " + prob.getMessage());
			//exitOnError();
			return "PROBLEM";
		}
		else if (type instanceof ICPPTemplateTypeParameter)
		{
			ICPPTemplateTypeParameter template = (ICPPTemplateTypeParameter) type;
			MyLogger.logImportant("template type");
			return template.toString();
		}
		else if (type != null)
		{
			MyLogger.logImportant("Unknown type: " + type.getClass().getCanonicalName() + type.toString());
			MyLogger.exitOnError();
		}
		return null;
	}
	
	/**
	 * Attempts to get the boxed type for a primitive type.
	 * We need the boxed type to use for Ptr and Ref parametized
	 * types.
	 */
	static String evaluateSimpleTypeBoxed(IBasicType.Kind type, boolean isShort, boolean isLongLong, boolean isUnsigned)
	{
		switch (type)
		{
		case eChar:
			MyLogger.log("char");
			return "Byte";
		case eInt:
			MyLogger.log("int");
			if (isShort)
				return "Short";
			else if (isLongLong)
				return "Long";
			else
				return "Integer";
		case eFloat:
			MyLogger.log("float");
			return "Float";
		case eDouble:
			MyLogger.log("double");
			return "Double";
		case eUnspecified:
			MyLogger.log("unspecified");
			if (isUnsigned)
				return "Integer";
			else
				return "Integer";
		case eVoid:
			MyLogger.log("void");
			return "Void";
		case eBoolean:
			MyLogger.log("bool");
			return "Boolean";
		case eChar16:
		case eWChar:
			MyLogger.log("wchar_t");
			return "Character";
		default:
			return null;
		}
	}
	
	/**
	 * Returns the Java simple type for the corresponding C++ type. 
	 */
	static String evaluateSimpleType(IBasicType.Kind type, boolean isShort, boolean isLongLong, boolean isUnsigned)
	{
		switch (type)
		{
		case eChar:
			MyLogger.log("char");
			return "byte";
		case eInt:
			MyLogger.log("int");
			if (isShort)
				return "short";
			else if (isLongLong)
				return "long";
			else
				return "int";
		case eFloat:
			MyLogger.log("float");
			return "float";
		case eDouble:
			MyLogger.log("double");
			return "double";
		case eUnspecified:
			MyLogger.log("unspecified");
			if (isUnsigned)
				return "int";
			else
				return "int";
		case eVoid:
			MyLogger.log("void");
			return "void";
		case eBoolean:
			MyLogger.log("bool");
			return "boolean";
		case eChar16:
		case eWChar:
			MyLogger.log("wchar_t");
			return "char";
		default:
			return null;
		}
	}
	
	/**
	 * Gets a simple type. Eg. WebCore::RenderObject becomes
	 * RenderObject.
	 */
	static String getSimpleType(String qualifiedType)
	{
		String ret;

		if (qualifiedType.contains("::"))
		{
			ret = qualifiedType.substring(qualifiedType.lastIndexOf("::"));
		}
		else
			ret = qualifiedType;

		if (ret.isEmpty())
			return "MISSING";
		else
			return ret;
	}

	/**
	 * Replaces C++ names with Java compatible names for functions.
	 * You may need to add missing operators.
	 */
	static String normalizeName(String name)
	{
		String replace;
		if (name.startsWith("operator"))
		{
			if (name.equals("operator +="))
				replace = "opPlusAssign";
			else if (name.equals("operator =="))
				replace = "equals";
			else if (name.equals("operator -="))
				replace = "opMinusAssign";
			else if (name.equals("operator !="))
				replace = "opNotEquals";
			else if (name.equals("operator !"))
				replace = "opNot";
			else if (name.equals("operator ->"))
				replace = "opAccess";
			else if (name.equals("operator |"))
				replace = "opOr";
			else if (name.equals("operator -"))
				replace = "opMinus";
			else if (name.equals("operator +"))
				replace = "opPlus";
			else if (name.equals("operator *"))
				replace = "opStar";
			else if (name.equals("operator &"))
				replace = "opAddressOf";
			else if (name.equals("operator []"))
				replace = "opArrayAccess";
			else if (name.equals("operator new[]"))
				replace = "opNewArray";
			else if (name.equals("operator delete[]"))
				replace = "opDeleteArray";
			else if (name.equals("operator ="))
				replace = "opAssign";
			else if (name.equals("operator |="))
				replace = "opOrAssign";
			else if (name.equals("operator new"))
				replace = "opNew";
			else if (name.equals("operator delete"))
				replace = "opDelete";
			else
				replace = "__PROBLEM__";
		}
		else if (name.startsWith("~"))
			replace = "destruct";
		else if (name.equals("bool"))
			replace = "Boolean";
		else if (name.equals("byte"))
			replace = "Byte";
		else if (name.equals("char"))
			replace = "Character";
		else if (name.equals("short"))
			replace = "Short";
		else if (name.equals("int"))
			replace = "Integer";
		else if (name.equals("long"))
			replace = "Long";
		else if (name.equals("float"))
			replace = "Float";
		else if (name.equals("double"))
			replace = "Double";
		else if (name.equals("String"))
			replace = "CppString";
		else
			replace = name // Cast operators need cleaning.
			.replace(' ', '_')
			.replace(':', '_')
			.replace('&', '_')
			.replace('(', '_')
			.replace(')', '_')
			.replace('*', '_')
			.replace('<', '_')
			.replace('>', '_')
			.replace(',', '_');

		if (replace.isEmpty())
			replace = "MISSING";
		
		return replace;
	}

	/**
	 * Gets a simplified Java compatible name.
	 */
	static String getSimpleName(IASTName name) throws DOMException
	{
		String nm = name.resolveBinding().getName();
		nm = normalizeName(nm);

		MyLogger.log("name: " + name.resolveBinding().getName() + ":" + nm);
		return nm;
	}
	
	/**
	 * Gets our enum TypeEnum of an IType.
	 */
	private static TypeEnum getTypeEnum(IType type) throws DOMException
	{
		type = expand(type);
		
		if (type instanceof IBasicType)
		{
			if (((IBasicType) type).getKind() == IBasicType.Kind.eBoolean)
			{
				return TypeEnum.BOOLEAN;
			}

			if (((IBasicType) type).getKind() == IBasicType.Kind.eChar16 ||
				((IBasicType) type).getKind() == IBasicType.Kind.eWChar)
			{
				return TypeEnum.CHAR;
			}

			if (((IBasicType) type).getKind() != IBasicType.Kind.eVoid)
			{
				return TypeEnum.NUMBER;
			}
		
			if (((IBasicType) type).getKind() == IBasicType.Kind.eVoid)
			{
				return TypeEnum.VOID;
			}
		}
		
		if (type instanceof IFunctionType)
		{
			return TypeEnum.FUNCTION;
		}

		if (type instanceof IPointerType)
		{
			type = getPointerBaseType(type);
			
			if (isOneOf(type, TypeEnum.OBJECT))
				return TypeEnum.OBJECT_POINTER;
			else if (isOneOf(type, TypeEnum.BOOLEAN, TypeEnum.CHAR, TypeEnum.NUMBER))
				return TypeEnum.BASIC_POINTER;
			else if (isOneOf(type, TypeEnum.FUNCTION))
				return TypeEnum.FUNCTION_POINTER;
			else if (isOneOf(type, TypeEnum.VOID))
				return TypeEnum.VOID_POINTER;
		}

		if (type instanceof IArrayType)
		{
			type = getArrayBaseType(type);
			
			if (isOneOf(type, TypeEnum.OBJECT))
				return TypeEnum.OBJECT_ARRAY;
			else if (isOneOf(type, TypeEnum.BOOLEAN, TypeEnum.CHAR, TypeEnum.NUMBER))
				return TypeEnum.BASIC_ARRAY;
			else if (isOneOf(type, TypeEnum.FUNCTION))
				return TypeEnum.FUNCTION_ARRAY;
		}
			
		if (type instanceof ICPPReferenceType)
		{
			type = getReferenceBaseType(type);

			if (isOneOf(type, TypeEnum.OBJECT))
				return TypeEnum.OBJECT_REFERENCE;
			else if (isOneOf(type, TypeEnum.BOOLEAN, TypeEnum.CHAR, TypeEnum.NUMBER))
				return TypeEnum.BASIC_REFERENCE;
			else if (isOneOf(type, TypeEnum.FUNCTION))
				return TypeEnum.FUNCTION_REFERENCE;
		}

		if (type instanceof ICPPClassType)
		{
			return TypeEnum.OBJECT;
		}
		
		if (type instanceof ICPPTemplateTypeParameter)
		{
			return TypeEnum.OTHER;
		}
		
		if (type instanceof IEnumeration)
		{
			return TypeEnum.ENUMERATION;
		}
		
		if (type instanceof ICPPUnknownType)
		{
			return TypeEnum.UNKNOWN;
		}
		
		if (type instanceof IProblemType)
		{
			MyLogger.logImportant(((IProblemType) type).getMessage());
			return TypeEnum.UNKNOWN;
		}
			
		MyLogger.logImportant("Unknown type: " + type.getClass().getInterfaces()[0].toString());
		MyLogger.exitOnError();
		return null;
	}

	String getAnonymousClassName(IType tp)
	{
		String name = "AnonClass" + ctx.global.anonClassCount++;
		ctx.anonTypes.put(tp, name);
		return name;
	}
	
	static IType getReferenceBaseType(IType type) throws DOMException
	{
		while (type instanceof ICPPReferenceType ||
			   expand(type) instanceof ICPPReferenceType)
		{
			if (type instanceof ICPPReferenceType)
				type = ((ICPPReferenceType) type).getType();
			else
				type = ((ICPPReferenceType) expand(type)).getType();
		}
		
		return type;
	}
	
	/**
	 * Gets the base type of an array.
	 * @return CDT IType of array.
	 */
	static IType getArrayBaseType(IType type) throws DOMException
	{
		while (type instanceof IArrayType ||
			   expand(type) instanceof IArrayType)
		{
			if (type instanceof IArrayType)
				type = ((IArrayType) type).getType();
			else
				type = ((IArrayType) expand(type)).getType();
		}

		return type;
	}

	/**
	 * Gets the base type of a pointer.
	 * @return CDT IType of pointer.
	 */
	static IType getPointerBaseType(IType type) throws DOMException
	{
		while (type instanceof IPointerType ||
			   expand(type) instanceof IPointerType)
		{
			if (type instanceof IPointerType)
				type = ((IPointerType) type).getType();
			else
				type = ((IPointerType) expand(type)).getType();
		}
		
		return type;
	}
	
	static int getPointerIndirectionCount(IType type) throws DOMException
	{
		int cnt = 0;
		
		while (type instanceof IPointerType ||
			   expand(type) instanceof IPointerType)
		{
			cnt++;
			
			if (type instanceof IPointerType)
				type = ((IPointerType) type).getType();
			else
				type = ((IPointerType) expand(type)).getType();
		}

		return cnt;
	}
	
	static boolean decaysToPointer(IType type) throws DOMException
	{
		type = getReferenceBaseType(type);
		
		return TypeManager.isOneOf(type, TypeEnum.OBJECT_POINTER,
			TypeEnum.BASIC_POINTER, TypeEnum.FUNCTION_POINTER,
			TypeEnum.VOID_POINTER, TypeEnum.BASIC_ARRAY, TypeEnum.OBJECT_ARRAY,
			TypeEnum.FUNCTION_ARRAY);
	}
	
	/**
	 * Gets the complete C++ qualified name.
	 */
	static String getCompleteName(IASTName name) throws DOMException
	{
		IBinding binding = name.resolveBinding();

		if (binding instanceof ICPPBinding)
		{
			ICPPBinding cpp = (ICPPBinding) binding;
			String names[] = cpp.getQualifiedName();
			StringBuilder ret = new StringBuilder(); 

			for (int i = 0; i < names.length; i++)
			{
				ret.append(names[i]);
				if (i != names.length - 1) 
					ret.append("::");
			}

			MyLogger.log("Complete Name: " + ret);
			return ret.toString();
		}

		return binding.getName();
	}

	/**
	 * Gets the qualifier part of a name.
	 */
	static String getQualifiedPart(IASTName name) throws DOMException
	{
		IBinding binding = name.resolveBinding();

		if (binding instanceof ICPPBinding)
		{
			ICPPBinding cpp = (ICPPBinding) binding;
			String names[] = cpp.getQualifiedName();
			StringBuilder ret = new StringBuilder();

			for (int i = 0; i < names.length - 1; i++)
			{
				ret.append(names[i]);
				if (i != names.length - 2) 
					ret.append("::");
			}

			MyLogger.log("Qualified Name found: " + ret);
			return ret.toString();
		}

		return "";
	}
}
