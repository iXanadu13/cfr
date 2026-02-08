package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.loc.BytecodeLoc;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.VarArgsRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.misc.Precedence;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.rewriteinterface.FunctionProcessor;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.FieldVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.SentinelLocalClassLValue;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.CloneHelper;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.bytecode.analysis.types.*;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.entities.ClassFileField;
import org.benf.cfr.reader.entities.classfilehelpers.OverloadMethodSet;
import org.benf.cfr.reader.entities.exceptions.ExceptionCheck;
import org.benf.cfr.reader.state.InnerClassTypeUsageInformation;
import org.benf.cfr.reader.state.TypeUsageInformation;
import org.benf.cfr.reader.util.MiscConstants;
import org.benf.cfr.reader.util.output.Dumper;
import org.benf.cfr.reader.util.output.TypeContext;

import java.util.List;

public class ConstructorInvokationSimple extends AbstractConstructorInvokation implements FunctionProcessor {

    private final MemberFunctionInvokation constructorInvokation;
    private InferredJavaType constructionType;

    public ConstructorInvokationSimple(BytecodeLoc loc,
                                       MemberFunctionInvokation constructorInvokation,
                                       InferredJavaType inferredJavaType, InferredJavaType constructionType,
                                       List<Expression> args) {
        super(loc, inferredJavaType, constructorInvokation.getFunction(), args);
        this.constructorInvokation = constructorInvokation;
        this.constructionType = constructionType;
    }

    @Override
    public BytecodeLoc getCombinedLoc() {
        return BytecodeLoc.combine(this, getArgs(), constructorInvokation);
    }

    @Override
    public Expression deepClone(CloneHelper cloneHelper) {
        return new ConstructorInvokationSimple(getLoc(), constructorInvokation, getInferredJavaType(), constructionType, cloneHelper.replaceOrClone(getArgs()));
    }

    @Override
    public Precedence getPrecedence() {
        return Precedence.PAREN_SUB_MEMBER;
    }

    private JavaTypeInstance getFinalDisplayTypeInstance() {
        JavaTypeInstance res = constructionType.getJavaTypeInstance();
        if (!(res instanceof JavaGenericBaseInstance)) return res;
        if (!((JavaGenericBaseInstance) res).hasL01Wildcard()) return res;
        res = ((JavaGenericBaseInstance) res).getWithoutL01Wildcard();
        return res;
    }

    @Override
    public Dumper dumpInner(Dumper d) {
        JavaTypeInstance clazz = getFinalDisplayTypeInstance();
        List<Expression> args = getArgs();
        MethodPrototype prototype = constructorInvokation.getMethodPrototype();

        if (prototype.isInnerOuterThis() && prototype.isHiddenArg(0) && args.size() > 0) {
            Expression a1 = args.get(0);

            test : if (!a1.toString().equals(MiscConstants.THIS)) {
                if (a1 instanceof LValueExpression) {
                    LValue lValue = ((LValueExpression) a1).getLValue();
                    if (lValue instanceof FieldVariable) {
                        ClassFileField classFileField = ((FieldVariable) lValue).getClassFileField();
                        if (classFileField.isSyntheticOuterRef()) break test;
                    }
                }
                d.dump(a1).print('.');
            }
        }

        d.keyword("new ");

        dumpType(d, clazz, prototype);

        d.separator("(");
        boolean first = true;
        for (int i = 0; i < args.size(); ++i) {
            if (prototype.isHiddenArg(i)) continue;
            Expression arg = args.get(i);
            if (!first) d.print(", ");
            first = false;
            d.dump(arg);
        }
        d.separator(")");
        return d;
    }

    private void dumpType(Dumper d, JavaTypeInstance clazz, MethodPrototype prototype) {
        boolean isInitialization = prototype.isInstanceMethod() && prototype.isInnerOuterThis() && prototype.getName().equals("<init>");
        if (!isInitialization) {
            d.dump(clazz, TypeContext.INNER);
            return;
        }
        if (!(prototype.getReturnType() instanceof JavaRefTypeInstance)) {
            d.dump(clazz, TypeContext.INNER);
            return;
        }
        JavaTypeInstance ancestor = d.getTypeUsageInformation().getAnalysisType();
        JavaRefTypeInstance returnType = (JavaRefTypeInstance) prototype.getReturnType();
//        JavaRefTypeInstance superType = (JavaRefTypeInstance) prototype.getArgs().get(0);
        if (ancestor == null || !(clazz instanceof JavaRefTypeInstance)) {
            d.dump(clazz, TypeContext.INNER);
            return;
        }

        TypeUsageInformation current = d.getTypeUsageInformation();
        if (returnType.equals(d.getTypeUsageInformation().getCurrentScope())) {
            // just dump as normal
            d.dump(clazz, TypeContext.INNER);
            return;
        }
        while (current != null && current.getCurrentScope() != null && !current.getCurrentScope().equals(ancestor)) {
            // returns null if is not in inner class
            current = current.getDelegateTypeUsageInformation();
        }
        if (current == null) {
            // cannot find the outer scope
            d.dump(clazz, TypeContext.INNER);
            return;
        }
        if (returnType.equals(ancestor)) {
            TypeUsageInformation innerclassTypeUsageInformation = new InnerClassTypeUsageInformation(current, d.getTypeUsageInformation().getCurrentScope());
            Dumper d2 = d.withTypeUsageInformation(innerclassTypeUsageInformation);
            d2.dump(clazz, TypeContext.INNER);
        } else if (!current.hasLocalInstance(returnType)) {
            // see: InnerClassTest23_Strobel, InnerClassTest44
            d.dump(clazz, TypeContext.NEW);
        } else {
            d.dump(clazz, TypeContext.INNER);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (o == null) return false;
        if (!(o instanceof ConstructorInvokationSimple)) return false;

        return super.equals(o);
    }

    public static boolean isAnonymousMethodType(JavaTypeInstance lValueType) {
        InnerClassInfo innerClassInfo = lValueType.getInnerClassHereInfo();
        if (innerClassInfo.isMethodScopedClass() && !innerClassInfo.isAnonymousClass()) {
            return true;
        }
        return false;
    }

    @Override
    public void collectUsedLValues(LValueUsageCollector lValueUsageCollector) {
        JavaTypeInstance lValueType = constructorInvokation.getClassTypeInstance();
        if (isAnonymousMethodType(lValueType)) {
            lValueUsageCollector.collect(new SentinelLocalClassLValue(lValueType), ReadWrite.READ /* not strictly.. */);
        }
        super.collectUsedLValues(lValueUsageCollector);
    }


    @Override
    public boolean equivalentUnder(Object o, EquivalenceConstraint constraint) {
        if (!(o instanceof ConstructorInvokationSimple)) return false;
        if (!super.equivalentUnder(o, constraint)) return false;
        return true;
    }

    @Override
    public boolean canThrow(ExceptionCheck caught) {
        return caught.checkAgainst(constructorInvokation);
    }

    @Override
    public void rewriteVarArgs(VarArgsRewriter varArgsRewriter) {
        MethodPrototype methodPrototype = getMethodPrototype();
        if (!methodPrototype.isVarArgs()) return;
        OverloadMethodSet overloadMethodSet = getOverloadMethodSet();
        if (overloadMethodSet == null) return;
        GenericTypeBinder gtb = methodPrototype.getTypeBinderFor(getArgs());
        varArgsRewriter.rewriteVarArgsArg(overloadMethodSet, methodPrototype, getArgs(), gtb);
    }

    public MethodPrototype getConstructorPrototype() {
        return getMethodPrototype();
    }
}
