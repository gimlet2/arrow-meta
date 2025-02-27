package arrow.meta.dsl.codegen.ir

import arrow.meta.Meta
import arrow.meta.phases.CompilerContext
import arrow.meta.phases.codegen.ir.IRGeneration
import arrow.meta.phases.codegen.ir.IrUtils
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.IrAnonymousInitializer
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationBase
import org.jetbrains.kotlin.ir.declarations.IrEnumEntry
import org.jetbrains.kotlin.ir.declarations.IrErrorDeclaration
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrLocalDelegatedProperty
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrTypeAlias
import org.jetbrains.kotlin.ir.declarations.IrTypeParameter
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrBlock
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrBody
import org.jetbrains.kotlin.ir.expressions.IrBranch
import org.jetbrains.kotlin.ir.expressions.IrBreak
import org.jetbrains.kotlin.ir.expressions.IrBreakContinue
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrCallableReference
import org.jetbrains.kotlin.ir.expressions.IrCatch
import org.jetbrains.kotlin.ir.expressions.IrClassReference
import org.jetbrains.kotlin.ir.expressions.IrComposite
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrContainerExpression
import org.jetbrains.kotlin.ir.expressions.IrContinue
import org.jetbrains.kotlin.ir.expressions.IrDeclarationReference
import org.jetbrains.kotlin.ir.expressions.IrDelegatingConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrDoWhileLoop
import org.jetbrains.kotlin.ir.expressions.IrDynamicExpression
import org.jetbrains.kotlin.ir.expressions.IrDynamicMemberExpression
import org.jetbrains.kotlin.ir.expressions.IrDynamicOperatorExpression
import org.jetbrains.kotlin.ir.expressions.IrElseBranch
import org.jetbrains.kotlin.ir.expressions.IrEnumConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrErrorCallExpression
import org.jetbrains.kotlin.ir.expressions.IrErrorExpression
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrExpressionBody
import org.jetbrains.kotlin.ir.expressions.IrFieldAccessExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionAccessExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionReference
import org.jetbrains.kotlin.ir.expressions.IrGetClass
import org.jetbrains.kotlin.ir.expressions.IrGetEnumValue
import org.jetbrains.kotlin.ir.expressions.IrGetField
import org.jetbrains.kotlin.ir.expressions.IrGetObjectValue
import org.jetbrains.kotlin.ir.expressions.IrGetSingletonValue
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.IrInstanceInitializerCall
import org.jetbrains.kotlin.ir.expressions.IrLocalDelegatedPropertyReference
import org.jetbrains.kotlin.ir.expressions.IrLoop
import org.jetbrains.kotlin.ir.expressions.IrMemberAccessExpression
import org.jetbrains.kotlin.ir.expressions.IrPropertyReference
import org.jetbrains.kotlin.ir.expressions.IrReturn
import org.jetbrains.kotlin.ir.expressions.IrSetField
import org.jetbrains.kotlin.ir.expressions.IrSetValue
import org.jetbrains.kotlin.ir.expressions.IrSpreadElement
import org.jetbrains.kotlin.ir.expressions.IrStringConcatenation
import org.jetbrains.kotlin.ir.expressions.IrSuspendableExpression
import org.jetbrains.kotlin.ir.expressions.IrSuspensionPoint
import org.jetbrains.kotlin.ir.expressions.IrSyntheticBody
import org.jetbrains.kotlin.ir.expressions.IrThrow
import org.jetbrains.kotlin.ir.expressions.IrTry
import org.jetbrains.kotlin.ir.expressions.IrTypeOperatorCall
import org.jetbrains.kotlin.ir.expressions.IrValueAccessExpression
import org.jetbrains.kotlin.ir.expressions.IrVararg
import org.jetbrains.kotlin.ir.expressions.IrWhen
import org.jetbrains.kotlin.ir.expressions.IrWhileLoop
import org.jetbrains.kotlin.ir.util.KotlinLikeDumpOptions
import org.jetbrains.kotlin.ir.util.dump
import org.jetbrains.kotlin.ir.util.dumpKotlinLike
import org.jetbrains.kotlin.ir.visitors.IrElementTransformer

/**
 * The codegen phase is where the compiler emits bytecode and metadata for the different platforms
 * the Kotlin language targets. In this phase, by default, the compiler would go into ASM codegen
 * for the JVM, or into IR codegen if IR is enabled. [IR] is the Intermediate Representation format
 * the new Kotlin compiler backend targets.
 */
interface IrSyntax {

  /**
   * IR, The intermediate representation format, is a structured text format with significant
   * indentation that contains all the information the compiler knows about a program. At this
   * point, the compiler knows the structure of a program based on its sources, what the typed
   * expressions are, and how each of the generic type arguments gets applied. The compiler emits
   * information in this phase that is processed by interpreters and compilers targeting any
   * platform. [IR Example]
   */
  fun IrSyntax.IrGeneration(
    generate:
      (
        compilerContext: CompilerContext,
        moduleFragment: IrModuleFragment,
        pluginContext: IrPluginContext) -> Unit
  ): IRGeneration =
    object : IRGeneration {
      override fun CompilerContext.generate(
        moduleFragment: IrModuleFragment,
        pluginContext: IrPluginContext
      ) {
        generate(this, moduleFragment, pluginContext)
      }
    }

  fun Meta.irModuleFragment(f: IrUtils.(IrModuleFragment) -> IrModuleFragment?): IRGeneration =
      IrGeneration { compilerContext, moduleFragment, pluginContext ->
    moduleFragment.transform(
      object : IrElementTransformer<Unit> {
        override fun visitModuleFragment(
          declaration: IrModuleFragment,
          data: Unit
        ): IrModuleFragment =
          declaration.transformChildren(this, Unit).let {
            f(IrUtils(pluginContext, compilerContext, moduleFragment), declaration)
              ?: super.visitModuleFragment(declaration, data)
          }
      },
      Unit
    )
  }

  fun Meta.irFile(f: IrUtils.(IrFile) -> IrFile?): IRGeneration =
      IrGeneration { compilerContext, moduleFragment, pluginContext ->
    moduleFragment.transformChildren(
      object : IrElementTransformer<Unit> {
        override fun visitFile(declaration: IrFile, data: Unit): IrFile =
          declaration.transformChildren(this, Unit).let {
            f(IrUtils(pluginContext, compilerContext, moduleFragment), declaration)
              ?: super.visitFile(declaration, data)
          }
      },
      Unit
    )
  }

  fun Meta.irDeclaration(f: IrUtils.(IrDeclaration) -> IrStatement?): IRGeneration =
      IrGeneration { compilerContext, moduleFragment, pluginContext ->
    moduleFragment.transformChildren(
      object : IrElementTransformer<Unit> {
        override fun visitDeclaration(expression: IrDeclarationBase, data: Unit): IrStatement =
          expression.transformChildren(this, Unit).let {
            f(IrUtils(pluginContext, compilerContext, moduleFragment), expression)
              ?: super.visitDeclaration(expression, data)
          }
      },
      Unit
    )
  }

  fun Meta.irClass(f: IrUtils.(IrClass) -> IrStatement?): IRGeneration =
      IrGeneration { compilerContext, moduleFragment, pluginContext ->
    moduleFragment.transformChildren(
      object : IrElementTransformer<Unit> {
        override fun visitClass(expression: IrClass, data: Unit): IrStatement =
          expression.transformChildren(this, Unit).let {
            f(IrUtils(pluginContext, compilerContext, moduleFragment), expression)
              ?: super.visitClass(expression, data)
          }
      },
      Unit
    )
  }

  fun Meta.irFunction(f: IrUtils.(IrFunction) -> IrStatement?): IRGeneration =
      IrGeneration { compilerContext, moduleFragment, pluginContext ->
    moduleFragment.transformChildren(
      object : IrElementTransformer<Unit> {
        override fun visitFunction(expression: IrFunction, data: Unit): IrStatement =
          expression.transformChildren(this, Unit).let {
            f(IrUtils(pluginContext, compilerContext, moduleFragment), expression)
              ?: super.visitFunction(expression, data)
          }
      },
      Unit
    )
  }

  fun Meta.irSimpleFunction(f: IrUtils.(IrSimpleFunction) -> IrStatement?): IRGeneration =
      IrGeneration { compilerContext, moduleFragment, pluginContext ->
    moduleFragment.transformChildren(
      object : IrElementTransformer<Unit> {
        override fun visitSimpleFunction(declaration: IrSimpleFunction, data: Unit): IrStatement =
          declaration.transformChildren(this, Unit).let {
            f(IrUtils(pluginContext, compilerContext, moduleFragment), declaration)
              ?: super.visitSimpleFunction(declaration, data)
          }
      },
      Unit
    )
  }

  fun Meta.irConstructor(f: IrUtils.(IrConstructor) -> IrStatement?): IRGeneration =
      IrGeneration { compilerContext, moduleFragment, pluginContext ->
    moduleFragment.transformChildren(
      object : IrElementTransformer<Unit> {
        override fun visitConstructor(declaration: IrConstructor, data: Unit): IrStatement =
          declaration.transformChildren(this, Unit).let {
            f(IrUtils(pluginContext, compilerContext, moduleFragment), declaration)
              ?: super.visitConstructor(declaration, data)
          }
      },
      Unit
    )
  }

  fun Meta.irProperty(f: IrUtils.(IrProperty) -> IrStatement?): IRGeneration =
      IrGeneration { compilerContext, moduleFragment, pluginContext ->
    moduleFragment.transformChildren(
      object : IrElementTransformer<Unit> {
        override fun visitProperty(declaration: IrProperty, data: Unit): IrStatement =
          declaration.transformChildren(this, Unit).let {
            f(IrUtils(pluginContext, compilerContext, moduleFragment), declaration)
              ?: super.visitProperty(declaration, data)
          }
      },
      Unit
    )
  }

  fun Meta.irField(f: IrUtils.(IrField) -> IrStatement?): IRGeneration =
      IrGeneration { compilerContext, moduleFragment, pluginContext ->
    moduleFragment.transformChildren(
      object : IrElementTransformer<Unit> {
        override fun visitField(declaration: IrField, data: Unit): IrStatement =
          declaration.transformChildren(this, Unit).let {
            f(IrUtils(pluginContext, compilerContext, moduleFragment), declaration)
              ?: super.visitField(declaration, data)
          }
      },
      Unit
    )
  }

  fun Meta.irLocalDelegatedProperty(
    f: IrUtils.(IrLocalDelegatedProperty) -> IrStatement?
  ): IRGeneration = IrGeneration { compilerContext, moduleFragment, pluginContext ->
    moduleFragment.transformChildren(
      object : IrElementTransformer<Unit> {
        override fun visitLocalDelegatedProperty(
          declaration: IrLocalDelegatedProperty,
          data: Unit
        ): IrStatement =
          declaration.transformChildren(this, Unit).let {
            f(IrUtils(pluginContext, compilerContext, moduleFragment), declaration)
              ?: super.visitLocalDelegatedProperty(declaration, data)
          }
      },
      Unit
    )
  }

  fun Meta.irEnumEntry(f: IrUtils.(IrEnumEntry) -> IrStatement?): IRGeneration =
      IrGeneration { compilerContext, moduleFragment, pluginContext ->
    moduleFragment.transformChildren(
      object : IrElementTransformer<Unit> {
        override fun visitEnumEntry(declaration: IrEnumEntry, data: Unit): IrStatement =
          declaration.transformChildren(this, Unit).let {
            f(IrUtils(pluginContext, compilerContext, moduleFragment), declaration)
              ?: super.visitEnumEntry(declaration, data)
          }
      },
      Unit
    )
  }

  fun Meta.irAnonymousInitializer(
    f: IrUtils.(IrAnonymousInitializer) -> IrStatement?
  ): IRGeneration = IrGeneration { compilerContext, moduleFragment, pluginContext ->
    moduleFragment.transformChildren(
      object : IrElementTransformer<Unit> {
        override fun visitAnonymousInitializer(
          declaration: IrAnonymousInitializer,
          data: Unit
        ): IrStatement =
          declaration.transformChildren(this, Unit).let {
            f(IrUtils(pluginContext, compilerContext, moduleFragment), declaration)
              ?: super.visitAnonymousInitializer(declaration, data)
          }
      },
      Unit
    )
  }

  fun Meta.irVariable(f: IrUtils.(IrVariable) -> IrStatement?): IRGeneration =
      IrGeneration { compilerContext, moduleFragment, pluginContext ->
    moduleFragment.transformChildren(
      object : IrElementTransformer<Unit> {
        override fun visitVariable(declaration: IrVariable, data: Unit): IrStatement =
          declaration.transformChildren(this, Unit).let {
            f(IrUtils(pluginContext, compilerContext, moduleFragment), declaration)
              ?: super.visitVariable(declaration, data)
          }
      },
      Unit
    )
  }

  fun Meta.irTypeParameter(f: IrUtils.(IrTypeParameter) -> IrStatement?): IRGeneration =
      IrGeneration { compilerContext, moduleFragment, pluginContext ->
    moduleFragment.transformChildren(
      object : IrElementTransformer<Unit> {
        override fun visitTypeParameter(declaration: IrTypeParameter, data: Unit): IrStatement =
          declaration.transformChildren(this, Unit).let {
            f(IrUtils(pluginContext, compilerContext, moduleFragment), declaration)
              ?: super.visitTypeParameter(declaration, data)
          }
      },
      Unit
    )
  }

  fun Meta.irValueParameter(f: IrUtils.(IrValueParameter) -> IrStatement?): IRGeneration =
      IrGeneration { compilerContext, moduleFragment, pluginContext ->
    moduleFragment.transformChildren(
      object : IrElementTransformer<Unit> {
        override fun visitValueParameter(declaration: IrValueParameter, data: Unit): IrStatement =
          declaration.transformChildren(this, Unit).let {
            f(IrUtils(pluginContext, compilerContext, moduleFragment), declaration)
              ?: super.visitValueParameter(declaration, data)
          }
      },
      Unit
    )
  }

  fun Meta.irTypeAlias(f: IrUtils.(IrTypeAlias) -> IrTypeAlias?): IRGeneration =
      IrGeneration { compilerContext, moduleFragment, pluginContext ->
    moduleFragment.transformChildren(
      object : IrElementTransformer<Unit> {
        override fun visitTypeAlias(declaration: IrTypeAlias, data: Unit): IrStatement =
          declaration.transformChildren(this, Unit).let {
            f(IrUtils(pluginContext, compilerContext, moduleFragment), declaration)
              ?: super.visitTypeAlias(declaration, data)
          }
      },
      Unit
    )
  }

  fun Meta.irBody(f: IrUtils.(IrBody) -> IrBody?): IRGeneration =
      IrGeneration { compilerContext, moduleFragment, pluginContext ->
    moduleFragment.transformChildren(
      object : IrElementTransformer<Unit> {
        override fun visitBody(body: IrBody, data: Unit): IrBody =
          body.transformChildren(this, Unit).let {
            f(IrUtils(pluginContext, compilerContext, moduleFragment), body)
              ?: super.visitBody(body, data)
          }
      },
      Unit
    )
  }

  fun Meta.irExpressionBody(f: IrUtils.(IrExpressionBody) -> IrBody?): IRGeneration =
      IrGeneration { compilerContext, moduleFragment, pluginContext ->
    moduleFragment.transformChildren(
      object : IrElementTransformer<Unit> {
        override fun visitExpressionBody(body: IrExpressionBody, data: Unit): IrBody =
          body.transformChildren(this, Unit).let {
            f(IrUtils(pluginContext, compilerContext, moduleFragment), body)
              ?: super.visitExpressionBody(body, data)
          }
      },
      Unit
    )
  }

  fun Meta.irBlockBody(f: IrUtils.(IrBlockBody) -> IrBody?): IRGeneration =
      IrGeneration { compilerContext, moduleFragment, pluginContext ->
    moduleFragment.transformChildren(
      object : IrElementTransformer<Unit> {
        override fun visitBlockBody(body: IrBlockBody, data: Unit): IrBody =
          body.transformChildren(this, Unit).let {
            f(IrUtils(pluginContext, compilerContext, moduleFragment), body)
              ?: super.visitBlockBody(body, data)
          }
      },
      Unit
    )
  }

  fun Meta.irSyntheticBody(f: IrUtils.(IrSyntheticBody) -> IrBody?): IRGeneration =
      IrGeneration { compilerContext, moduleFragment, pluginContext ->
    moduleFragment.transformChildren(
      object : IrElementTransformer<Unit> {
        override fun visitSyntheticBody(body: IrSyntheticBody, data: Unit): IrBody =
          body.transformChildren(this, Unit).let {
            f(IrUtils(pluginContext, compilerContext, moduleFragment), body)
              ?: super.visitSyntheticBody(body, data)
          }
      },
      Unit
    )
  }

  fun Meta.irSuspendableExpression(
    f: IrUtils.(IrSuspendableExpression) -> IrExpression?
  ): IRGeneration = IrGeneration { compilerContext, moduleFragment, pluginContext ->
    moduleFragment.transformChildren(
      object : IrElementTransformer<Unit> {
        override fun visitSuspendableExpression(
          expression: IrSuspendableExpression,
          data: Unit
        ): IrExpression =
          expression.transformChildren(this, Unit).let {
            f(IrUtils(pluginContext, compilerContext, moduleFragment), expression)
              ?: super.visitSuspendableExpression(expression, data)
          }
      },
      Unit
    )
  }

  fun Meta.irSuspensionPoint(f: IrUtils.(IrSuspensionPoint) -> IrExpression?): IRGeneration =
      IrGeneration { compilerContext, moduleFragment, pluginContext ->
    moduleFragment.transformChildren(
      object : IrElementTransformer<Unit> {
        override fun visitSuspensionPoint(expression: IrSuspensionPoint, data: Unit): IrExpression =
          expression.transformChildren(this, Unit).let {
            f(IrUtils(pluginContext, compilerContext, moduleFragment), expression)
              ?: super.visitSuspensionPoint(expression, data)
          }
      },
      Unit
    )
  }

  fun Meta.irExpression(f: IrUtils.(IrExpression) -> IrExpression?): IRGeneration =
      IrGeneration { compilerContext, moduleFragment, pluginContext ->
    moduleFragment.transformChildren(
      object : IrElementTransformer<Unit> {
        override fun visitExpression(expression: IrExpression, data: Unit): IrExpression =
          expression.transformChildren(this, Unit).let {
            f(IrUtils(pluginContext, compilerContext, moduleFragment), expression)
              ?: super.visitExpression(expression, data)
          }
      },
      Unit
    )
  }

  fun Meta.irConst(f: IrUtils.(IrConst<*>) -> IrExpression?): IRGeneration =
      IrGeneration { compilerContext, moduleFragment, pluginContext ->
    moduleFragment.transformChildren(
      object : IrElementTransformer<Unit> {
        override fun <T> visitConst(expression: IrConst<T>, data: Unit): IrExpression =
          expression.transformChildren(this, Unit).let {
            f(IrUtils(pluginContext, compilerContext, moduleFragment), expression)
              ?: super.visitConst(expression, data)
          }
      },
      Unit
    )
  }

  fun Meta.irVararg(f: IrUtils.(IrVararg) -> IrExpression?): IRGeneration =
      IrGeneration { compilerContext, moduleFragment, pluginContext ->
    moduleFragment.transformChildren(
      object : IrElementTransformer<Unit> {
        override fun visitVararg(expression: IrVararg, data: Unit): IrExpression =
          expression.transformChildren(this, Unit).let {
            f(IrUtils(pluginContext, compilerContext, moduleFragment), expression)
              ?: super.visitVararg(expression, data)
          }
      },
      Unit
    )
  }

  fun Meta.irSpreadElement(f: IrUtils.(IrSpreadElement) -> IrSpreadElement?): IRGeneration =
      IrGeneration { compilerContext, moduleFragment, pluginContext ->
    moduleFragment.transformChildren(
      object : IrElementTransformer<Unit> {
        override fun visitSpreadElement(spread: IrSpreadElement, data: Unit): IrSpreadElement =
          spread.transformChildren(this, Unit).let {
            f(IrUtils(pluginContext, compilerContext, moduleFragment), spread)
              ?: super.visitSpreadElement(spread, data)
          }
      },
      Unit
    )
  }

  fun Meta.irContainerExpression(
    f: IrUtils.(IrContainerExpression) -> IrExpression?
  ): IRGeneration = IrGeneration { compilerContext, moduleFragment, pluginContext ->
    moduleFragment.transformChildren(
      object : IrElementTransformer<Unit> {
        override fun visitContainerExpression(
          expression: IrContainerExpression,
          data: Unit
        ): IrExpression =
          expression.transformChildren(this, Unit).let {
            f(IrUtils(pluginContext, compilerContext, moduleFragment), expression)
              ?: super.visitContainerExpression(expression, data)
          }
      },
      Unit
    )
  }

  fun Meta.irBlock(f: IrUtils.(IrBlock) -> IrExpression?): IRGeneration =
      IrGeneration { compilerContext, moduleFragment, pluginContext ->
    moduleFragment.transformChildren(
      object : IrElementTransformer<Unit> {
        override fun visitBlock(expression: IrBlock, data: Unit): IrExpression =
          expression.transformChildren(this, Unit).let {
            f(IrUtils(pluginContext, compilerContext, moduleFragment), expression)
              ?: super.visitBlock(expression, data)
          }
      },
      Unit
    )
  }

  fun Meta.irComposite(f: IrUtils.(IrComposite) -> IrExpression?): IRGeneration =
      IrGeneration { compilerContext, moduleFragment, pluginContext ->
    moduleFragment.transformChildren(
      object : IrElementTransformer<Unit> {
        override fun visitComposite(expression: IrComposite, data: Unit): IrExpression =
          expression.transformChildren(this, Unit).let {
            f(IrUtils(pluginContext, compilerContext, moduleFragment), expression)
              ?: super.visitComposite(expression, data)
          }
      },
      Unit
    )
  }

  fun Meta.irStringConcatenation(
    f: IrUtils.(IrStringConcatenation) -> IrExpression?
  ): IRGeneration = IrGeneration { compilerContext, moduleFragment, pluginContext ->
    moduleFragment.transformChildren(
      object : IrElementTransformer<Unit> {
        override fun visitStringConcatenation(
          expression: IrStringConcatenation,
          data: Unit
        ): IrExpression =
          expression.transformChildren(this, Unit).let {
            f(IrUtils(pluginContext, compilerContext, moduleFragment), expression)
              ?: super.visitStringConcatenation(expression, data)
          }
      },
      Unit
    )
  }

  fun Meta.irDeclarationReference(
    f: IrUtils.(IrDeclarationReference) -> IrExpression?
  ): IRGeneration = IrGeneration { compilerContext, moduleFragment, pluginContext ->
    moduleFragment.transformChildren(
      object : IrElementTransformer<Unit> {
        override fun visitDeclarationReference(
          expression: IrDeclarationReference,
          data: Unit
        ): IrExpression =
          expression.transformChildren(this, Unit).let {
            f(IrUtils(pluginContext, compilerContext, moduleFragment), expression)
              ?: super.visitDeclarationReference(expression, data)
          }
      },
      Unit
    )
  }

  fun Meta.irSingletonReference(f: IrUtils.(IrGetSingletonValue) -> IrExpression?): IRGeneration =
      IrGeneration { compilerContext, moduleFragment, pluginContext ->
    moduleFragment.transformChildren(
      object : IrElementTransformer<Unit> {
        override fun visitSingletonReference(
          expression: IrGetSingletonValue,
          data: Unit
        ): IrExpression =
          expression.transformChildren(this, Unit).let {
            f(IrUtils(pluginContext, compilerContext, moduleFragment), expression)
              ?: super.visitSingletonReference(expression, data)
          }
      },
      Unit
    )
  }

  fun Meta.irGetObjectValue(f: IrUtils.(IrGetObjectValue) -> IrExpression?): IRGeneration =
      IrGeneration { compilerContext, moduleFragment, pluginContext ->
    moduleFragment.transformChildren(
      object : IrElementTransformer<Unit> {
        override fun visitGetObjectValue(expression: IrGetObjectValue, data: Unit): IrExpression =
          expression.transformChildren(this, Unit).let {
            f(IrUtils(pluginContext, compilerContext, moduleFragment), expression)
              ?: super.visitGetObjectValue(expression, data)
          }
      },
      Unit
    )
  }

  fun Meta.irGetEnumValue(f: IrUtils.(IrGetEnumValue) -> IrExpression?): IRGeneration =
      IrGeneration { compilerContext, moduleFragment, pluginContext ->
    moduleFragment.transformChildren(
      object : IrElementTransformer<Unit> {
        override fun visitGetEnumValue(expression: IrGetEnumValue, data: Unit): IrExpression =
          expression.transformChildren(this, Unit).let {
            f(IrUtils(pluginContext, compilerContext, moduleFragment), expression)
              ?: super.visitGetEnumValue(expression, data)
          }
      },
      Unit
    )
  }

  fun Meta.irValueAccess(f: IrUtils.(IrValueAccessExpression) -> IrExpression?): IRGeneration =
      IrGeneration { compilerContext, moduleFragment, pluginContext ->
    moduleFragment.transformChildren(
      object : IrElementTransformer<Unit> {
        override fun visitValueAccess(
          expression: IrValueAccessExpression,
          data: Unit
        ): IrExpression =
          expression.transformChildren(this, Unit).let {
            f(IrUtils(pluginContext, compilerContext, moduleFragment), expression)
              ?: super.visitValueAccess(expression, data)
          }
      },
      Unit
    )
  }

  fun Meta.irGetValue(f: IrUtils.(IrGetValue) -> IrExpression?): IRGeneration =
      IrGeneration { compilerContext, moduleFragment, pluginContext ->
    moduleFragment.transformChildren(
      object : IrElementTransformer<Unit> {
        override fun visitGetValue(expression: IrGetValue, data: Unit): IrExpression =
          expression.transformChildren(this, Unit).let {
            f(IrUtils(pluginContext, compilerContext, moduleFragment), expression)
              ?: super.visitGetValue(expression, data)
          }
      },
      Unit
    )
  }

  fun Meta.irSetValue(f: IrUtils.(IrSetValue) -> IrExpression?): IRGeneration =
      IrGeneration { compilerContext, moduleFragment, pluginContext ->
    moduleFragment.transformChildren(
      object : IrElementTransformer<Unit> {
        override fun visitSetValue(expression: IrSetValue, data: Unit): IrExpression =
          expression.transformChildren(this, Unit).let {
            f(IrUtils(pluginContext, compilerContext, moduleFragment), expression)
              ?: super.visitSetValue(expression, data)
          }
      },
      Unit
    )
  }

  fun Meta.irFieldAccess(f: IrUtils.(IrFieldAccessExpression) -> IrExpression?): IRGeneration =
      IrGeneration { compilerContext, moduleFragment, pluginContext ->
    moduleFragment.transformChildren(
      object : IrElementTransformer<Unit> {
        override fun visitFieldAccess(
          expression: IrFieldAccessExpression,
          data: Unit
        ): IrExpression =
          expression.transformChildren(this, Unit).let {
            f(IrUtils(pluginContext, compilerContext, moduleFragment), expression)
              ?: super.visitFieldAccess(expression, data)
          }
      },
      Unit
    )
  }

  fun Meta.irGetField(f: IrUtils.(IrGetField) -> IrExpression?): IRGeneration =
      IrGeneration { compilerContext, moduleFragment, pluginContext ->
    moduleFragment.transformChildren(
      object : IrElementTransformer<Unit> {
        override fun visitGetField(expression: IrGetField, data: Unit): IrExpression =
          expression.transformChildren(this, Unit).let {
            f(IrUtils(pluginContext, compilerContext, moduleFragment), expression)
              ?: super.visitGetField(expression, data)
          }
      },
      Unit
    )
  }

  fun Meta.irSetField(f: IrUtils.(IrSetField) -> IrExpression?): IRGeneration =
      IrGeneration { compilerContext, moduleFragment, pluginContext ->
    moduleFragment.transformChildren(
      object : IrElementTransformer<Unit> {
        override fun visitSetField(expression: IrSetField, data: Unit): IrExpression =
          expression.transformChildren(this, Unit).let {
            f(IrUtils(pluginContext, compilerContext, moduleFragment), expression)
              ?: super.visitSetField(expression, data)
          }
      },
      Unit
    )
  }

  fun Meta.irMemberAccess(f: IrUtils.(IrMemberAccessExpression<*>) -> IrElement?): IRGeneration =
      IrGeneration { compilerContext, moduleFragment, pluginContext ->
    moduleFragment.transformChildren(
      object : IrElementTransformer<Unit> {
        override fun visitMemberAccess(
          expression: IrMemberAccessExpression<*>,
          data: Unit
        ): IrElement =
          expression.transformChildren(this, Unit).let {
            f(IrUtils(pluginContext, compilerContext, moduleFragment), expression)
              ?: super.visitMemberAccess(expression, data)
          }
      },
      Unit
    )
  }

  fun Meta.irFunctionAccess(f: IrUtils.(IrFunctionAccessExpression) -> IrElement?): IRGeneration =
      IrGeneration { compilerContext, moduleFragment, pluginContext ->
    moduleFragment.transformChildren(
      object : IrElementTransformer<Unit> {
        override fun visitFunctionAccess(
          expression: IrFunctionAccessExpression,
          data: Unit
        ): IrElement =
          expression.transformChildren(this, Unit).let {
            f(IrUtils(pluginContext, compilerContext, moduleFragment), expression)
              ?: super.visitFunctionAccess(expression, data)
          }
      },
      Unit
    )
  }

  fun Meta.irCall(f: IrUtils.(IrCall) -> IrElement?): IRGeneration =
      IrGeneration { compilerContext, moduleFragment, pluginContext ->
    moduleFragment.transformChildren(
      object : IrElementTransformer<Unit> {
        override fun visitCall(expression: IrCall, data: Unit): IrElement =
          expression.transformChildren(this, Unit).let {
            f(IrUtils(pluginContext, compilerContext, moduleFragment), expression)
              ?: super.visitCall(expression, data)
          }
      },
      Unit
    )
  }

  fun Meta.irConstructorCall(f: IrUtils.(IrConstructorCall) -> IrElement?): IRGeneration =
      IrGeneration { compilerContext, moduleFragment, pluginContext ->
    moduleFragment.transformChildren(
      object : IrElementTransformer<Unit> {
        override fun visitConstructorCall(expression: IrConstructorCall, data: Unit): IrElement =
          expression.transformChildren(this, Unit).let {
            f(IrUtils(pluginContext, compilerContext, moduleFragment), expression)
              ?: super.visitConstructorCall(expression, data)
          }
      },
      Unit
    )
  }

  fun Meta.irDelegatingConstructorCall(
    f: IrUtils.(IrDelegatingConstructorCall) -> IrElement?
  ): IRGeneration = IrGeneration { compilerContext, moduleFragment, pluginContext ->
    moduleFragment.transformChildren(
      object : IrElementTransformer<Unit> {
        override fun visitDelegatingConstructorCall(
          expression: IrDelegatingConstructorCall,
          data: Unit
        ): IrElement =
          expression.transformChildren(this, Unit).let {
            f(IrUtils(pluginContext, compilerContext, moduleFragment), expression)
              ?: super.visitDelegatingConstructorCall(expression, data)
          }
      },
      Unit
    )
  }

  fun Meta.irEnumConstructorCall(f: IrUtils.(IrEnumConstructorCall) -> IrElement?): IRGeneration =
      IrGeneration { compilerContext, moduleFragment, pluginContext ->
    moduleFragment.transformChildren(
      object : IrElementTransformer<Unit> {
        override fun visitEnumConstructorCall(
          expression: IrEnumConstructorCall,
          data: Unit
        ): IrElement =
          expression.transformChildren(this, Unit).let {
            f(IrUtils(pluginContext, compilerContext, moduleFragment), expression)
              ?: super.visitEnumConstructorCall(expression, data)
          }
      },
      Unit
    )
  }

  fun Meta.irGetClass(f: IrUtils.(IrGetClass) -> IrExpression?): IRGeneration =
      IrGeneration { compilerContext, moduleFragment, pluginContext ->
    moduleFragment.transformChildren(
      object : IrElementTransformer<Unit> {
        override fun visitGetClass(expression: IrGetClass, data: Unit): IrExpression =
          expression.transformChildren(this, Unit).let {
            f(IrUtils(pluginContext, compilerContext, moduleFragment), expression)
              ?: super.visitGetClass(expression, data)
          }
      },
      Unit
    )
  }

  fun Meta.irCallableReference(f: IrUtils.(IrCallableReference<*>) -> IrElement?): IRGeneration =
      IrGeneration { compilerContext, moduleFragment, pluginContext ->
    moduleFragment.transformChildren(
      object : IrElementTransformer<Unit> {
        override fun visitCallableReference(
          expression: IrCallableReference<*>,
          data: Unit
        ): IrElement =
          expression.transformChildren(this, Unit).let {
            f(IrUtils(pluginContext, compilerContext, moduleFragment), expression)
              ?: super.visitCallableReference(expression, data)
          }
      },
      Unit
    )
  }

  fun Meta.irFunctionReference(f: IrUtils.(IrFunctionReference) -> IrElement?): IRGeneration =
      IrGeneration { compilerContext, moduleFragment, pluginContext ->
    moduleFragment.transformChildren(
      object : IrElementTransformer<Unit> {
        override fun visitFunctionReference(
          expression: IrFunctionReference,
          data: Unit
        ): IrElement =
          expression.transformChildren(this, Unit).let {
            f(IrUtils(pluginContext, compilerContext, moduleFragment), expression)
              ?: super.visitFunctionReference(expression, data)
          }
      },
      Unit
    )
  }

  fun Meta.irPropertyReference(f: IrUtils.(IrPropertyReference) -> IrElement?): IRGeneration =
      IrGeneration { compilerContext, moduleFragment, pluginContext ->
    moduleFragment.transformChildren(
      object : IrElementTransformer<Unit> {
        override fun visitPropertyReference(
          expression: IrPropertyReference,
          data: Unit
        ): IrElement =
          expression.transformChildren(this, Unit).let {
            f(IrUtils(pluginContext, compilerContext, moduleFragment), expression)
              ?: super.visitPropertyReference(expression, data)
          }
      },
      Unit
    )
  }

  fun Meta.irLocalDelegatedPropertyReference(
    f: IrUtils.(IrLocalDelegatedPropertyReference) -> IrElement?
  ): IRGeneration = IrGeneration { compilerContext, moduleFragment, pluginContext ->
    moduleFragment.transformChildren(
      object : IrElementTransformer<Unit> {
        override fun visitLocalDelegatedPropertyReference(
          expression: IrLocalDelegatedPropertyReference,
          data: Unit
        ): IrElement =
          expression.transformChildren(this, Unit).let {
            f(IrUtils(pluginContext, compilerContext, moduleFragment), expression)
              ?: super.visitLocalDelegatedPropertyReference(expression, data)
          }
      },
      Unit
    )
  }

  fun Meta.irClassReference(f: IrUtils.(IrClassReference) -> IrExpression?): IRGeneration =
      IrGeneration { compilerContext, moduleFragment, pluginContext ->
    moduleFragment.transformChildren(
      object : IrElementTransformer<Unit> {
        override fun visitClassReference(expression: IrClassReference, data: Unit): IrExpression =
          expression.transformChildren(this, Unit).let {
            f(IrUtils(pluginContext, compilerContext, moduleFragment), expression)
              ?: super.visitClassReference(expression, data)
          }
      },
      Unit
    )
  }

  fun Meta.irInstanceInitializerCall(
    f: IrUtils.(IrInstanceInitializerCall) -> IrExpression?
  ): IRGeneration = IrGeneration { compilerContext, moduleFragment, pluginContext ->
    moduleFragment.transformChildren(
      object : IrElementTransformer<Unit> {
        override fun visitInstanceInitializerCall(
          expression: IrInstanceInitializerCall,
          data: Unit
        ): IrExpression =
          expression.transformChildren(this, Unit).let {
            f(IrUtils(pluginContext, compilerContext, moduleFragment), expression)
              ?: super.visitInstanceInitializerCall(expression, data)
          }
      },
      Unit
    )
  }

  fun Meta.irTypeOperator(f: IrUtils.(IrTypeOperatorCall) -> IrExpression?): IRGeneration =
      IrGeneration { compilerContext, moduleFragment, pluginContext ->
    moduleFragment.transformChildren(
      object : IrElementTransformer<Unit> {
        override fun visitTypeOperator(expression: IrTypeOperatorCall, data: Unit): IrExpression =
          expression.transformChildren(this, Unit).let {
            f(IrUtils(pluginContext, compilerContext, moduleFragment), expression)
              ?: super.visitTypeOperator(expression, data)
          }
      },
      Unit
    )
  }

  fun Meta.irWhen(f: IrUtils.(IrWhen) -> IrExpression?): IRGeneration =
      IrGeneration { compilerContext, moduleFragment, pluginContext ->
    moduleFragment.transformChildren(
      object : IrElementTransformer<Unit> {
        override fun visitWhen(expression: IrWhen, data: Unit): IrExpression =
          expression.transformChildren(this, Unit).let {
            f(IrUtils(pluginContext, compilerContext, moduleFragment), expression)
              ?: super.visitWhen(expression, data)
          }
      },
      Unit
    )
  }

  fun Meta.irBranch(f: IrUtils.(IrBranch) -> IrBranch?): IRGeneration =
      IrGeneration { compilerContext, moduleFragment, pluginContext ->
    moduleFragment.transformChildren(
      object : IrElementTransformer<Unit> {
        override fun visitBranch(branch: IrBranch, data: Unit): IrBranch =
          branch.transformChildren(this, Unit).let {
            f(IrUtils(pluginContext, compilerContext, moduleFragment), branch)
              ?: super.visitBranch(branch, data)
          }
      },
      Unit
    )
  }

  fun Meta.irElseBranch(f: IrUtils.(IrElseBranch) -> IrElseBranch?): IRGeneration =
      IrGeneration { compilerContext, moduleFragment, pluginContext ->
    moduleFragment.transformChildren(
      object : IrElementTransformer<Unit> {
        override fun visitElseBranch(branch: IrElseBranch, data: Unit): IrElseBranch =
          branch.transformChildren(this, Unit).let {
            f(IrUtils(pluginContext, compilerContext, moduleFragment), branch)
              ?: super.visitElseBranch(branch, data)
          }
      },
      Unit
    )
  }

  fun Meta.irLoop(f: IrUtils.(IrLoop) -> IrExpression?): IRGeneration =
      IrGeneration { compilerContext, moduleFragment, pluginContext ->
    moduleFragment.transformChildren(
      object : IrElementTransformer<Unit> {
        override fun visitLoop(loop: IrLoop, data: Unit): IrExpression =
          loop.transformChildren(this, Unit).let {
            f(IrUtils(pluginContext, compilerContext, moduleFragment), loop)
              ?: super.visitLoop(loop, data)
          }
      },
      Unit
    )
  }

  fun Meta.irWhileLoop(f: IrUtils.(IrWhileLoop) -> IrExpression?): IRGeneration =
      IrGeneration { compilerContext, moduleFragment, pluginContext ->
    moduleFragment.transformChildren(
      object : IrElementTransformer<Unit> {
        override fun visitWhileLoop(loop: IrWhileLoop, data: Unit): IrExpression =
          loop.transformChildren(this, Unit).let {
            f(IrUtils(pluginContext, compilerContext, moduleFragment), loop)
              ?: super.visitWhileLoop(loop, data)
          }
      },
      Unit
    )
  }

  fun Meta.irDoWhileLoop(f: IrUtils.(IrDoWhileLoop) -> IrExpression?): IRGeneration =
      IrGeneration { compilerContext, moduleFragment, pluginContext ->
    moduleFragment.transformChildren(
      object : IrElementTransformer<Unit> {
        override fun visitDoWhileLoop(loop: IrDoWhileLoop, data: Unit): IrExpression =
          loop.transformChildren(this, Unit).let {
            f(IrUtils(pluginContext, compilerContext, moduleFragment), loop)
              ?: super.visitDoWhileLoop(loop, data)
          }
      },
      Unit
    )
  }

  fun Meta.irTry(f: IrUtils.(IrTry) -> IrExpression?): IRGeneration =
      IrGeneration { compilerContext, moduleFragment, pluginContext ->
    moduleFragment.transformChildren(
      object : IrElementTransformer<Unit> {
        override fun visitTry(aTry: IrTry, data: Unit): IrExpression =
          aTry.transformChildren(this, Unit).let {
            f(IrUtils(pluginContext, compilerContext, moduleFragment), aTry)
              ?: super.visitTry(aTry, data)
          }
      },
      Unit
    )
  }

  fun Meta.irCatch(f: IrUtils.(IrCatch) -> IrCatch?): IRGeneration =
      IrGeneration { compilerContext, moduleFragment, pluginContext ->
    moduleFragment.transformChildren(
      object : IrElementTransformer<Unit> {
        override fun visitCatch(aCatch: IrCatch, data: Unit): IrCatch =
          aCatch.transformChildren(this, Unit).let {
            f(IrUtils(pluginContext, compilerContext, moduleFragment), aCatch)
              ?: super.visitCatch(aCatch, data)
          }
      },
      Unit
    )
  }

  fun Meta.irBreakContinue(f: IrUtils.(IrBreakContinue) -> IrExpression?): IRGeneration =
      IrGeneration { compilerContext, moduleFragment, pluginContext ->
    moduleFragment.transformChildren(
      object : IrElementTransformer<Unit> {
        override fun visitBreakContinue(jump: IrBreakContinue, data: Unit): IrExpression =
          jump.transformChildren(this, Unit).let {
            f(IrUtils(pluginContext, compilerContext, moduleFragment), jump)
              ?: super.visitBreakContinue(jump, data)
          }
      },
      Unit
    )
  }

  fun Meta.irBreak(f: IrUtils.(IrBreak) -> IrExpression?): IRGeneration =
      IrGeneration { compilerContext, moduleFragment, pluginContext ->
    moduleFragment.transformChildren(
      object : IrElementTransformer<Unit> {
        override fun visitBreak(jump: IrBreak, data: Unit): IrExpression =
          jump.transformChildren(this, Unit).let {
            f(IrUtils(pluginContext, compilerContext, moduleFragment), jump)
              ?: super.visitBreak(jump, data)
          }
      },
      Unit
    )
  }

  fun Meta.irContinue(f: IrUtils.(IrContinue) -> IrExpression?): IRGeneration =
      IrGeneration { compilerContext, moduleFragment, pluginContext ->
    moduleFragment.transformChildren(
      object : IrElementTransformer<Unit> {
        override fun visitContinue(jump: IrContinue, data: Unit): IrExpression =
          jump.transformChildren(this, Unit).let {
            f(IrUtils(pluginContext, compilerContext, moduleFragment), jump)
              ?: super.visitContinue(jump, data)
          }
      },
      Unit
    )
  }

  fun Meta.irReturn(f: IrUtils.(IrReturn) -> IrExpression?): IRGeneration =
      IrGeneration { compilerContext, moduleFragment, pluginContext ->
    moduleFragment.transformChildren(
      object : IrElementTransformer<Unit> {
        override fun visitReturn(expression: IrReturn, data: Unit): IrExpression =
          expression.transformChildren(this, Unit).let {
            f(IrUtils(pluginContext, compilerContext, moduleFragment), expression)
              ?: super.visitReturn(expression, data)
          }
      },
      Unit
    )
  }

  fun Meta.irThrow(f: IrUtils.(IrThrow) -> IrExpression?): IRGeneration =
      IrGeneration { compilerContext, moduleFragment, pluginContext ->
    moduleFragment.transformChildren(
      object : IrElementTransformer<Unit> {
        override fun visitThrow(expression: IrThrow, data: Unit): IrExpression =
          expression.transformChildren(this, Unit).let {
            f(IrUtils(pluginContext, compilerContext, moduleFragment), expression)
              ?: super.visitThrow(expression, data)
          }
      },
      Unit
    )
  }

  fun Meta.irDynamicExpression(f: IrUtils.(IrDynamicExpression) -> IrExpression?): IRGeneration =
      IrGeneration { compilerContext, moduleFragment, pluginContext ->
    moduleFragment.transformChildren(
      object : IrElementTransformer<Unit> {
        override fun visitDynamicExpression(
          expression: IrDynamicExpression,
          data: Unit
        ): IrExpression =
          expression.transformChildren(this, Unit).let {
            f(IrUtils(pluginContext, compilerContext, moduleFragment), expression)
              ?: super.visitDynamicExpression(expression, data)
          }
      },
      Unit
    )
  }

  fun Meta.irDynamicOperatorExpression(
    f: IrUtils.(IrDynamicOperatorExpression) -> IrExpression?
  ): IRGeneration = IrGeneration { compilerContext, moduleFragment, pluginContext ->
    moduleFragment.transformChildren(
      object : IrElementTransformer<Unit> {
        override fun visitDynamicOperatorExpression(
          expression: IrDynamicOperatorExpression,
          data: Unit
        ): IrExpression =
          expression.transformChildren(this, Unit).let {
            f(IrUtils(pluginContext, compilerContext, moduleFragment), expression)
              ?: super.visitDynamicOperatorExpression(expression, data)
          }
      },
      Unit
    )
  }

  fun Meta.irDynamicMemberExpression(
    f: IrUtils.(IrDynamicMemberExpression) -> IrExpression?
  ): IRGeneration = IrGeneration { compilerContext, moduleFragment, pluginContext ->
    moduleFragment.transformChildren(
      object : IrElementTransformer<Unit> {
        override fun visitDynamicMemberExpression(
          expression: IrDynamicMemberExpression,
          data: Unit
        ): IrExpression =
          expression.transformChildren(this, Unit).let {
            f(IrUtils(pluginContext, compilerContext, moduleFragment), expression)
              ?: super.visitDynamicMemberExpression(expression, data)
          }
      },
      Unit
    )
  }

  fun Meta.irErrorDeclaration(f: IrUtils.(IrErrorDeclaration) -> IrStatement?): IRGeneration =
      IrGeneration { compilerContext, moduleFragment, pluginContext ->
    moduleFragment.transformChildren(
      object : IrElementTransformer<Unit> {
        override fun visitErrorDeclaration(
          expression: IrErrorDeclaration,
          data: Unit
        ): IrStatement =
          expression.transformChildren(this, Unit).let {
            f(IrUtils(pluginContext, compilerContext, moduleFragment), expression)
              ?: super.visitErrorDeclaration(expression, data)
          }
      },
      Unit
    )
  }

  fun Meta.irErrorExpression(f: IrUtils.(IrErrorExpression) -> IrExpression?): IRGeneration =
      IrGeneration { compilerContext, moduleFragment, pluginContext ->
    moduleFragment.transformChildren(
      object : IrElementTransformer<Unit> {
        override fun visitErrorExpression(expression: IrErrorExpression, data: Unit): IrExpression =
          expression.transformChildren(this, Unit).let {
            f(IrUtils(pluginContext, compilerContext, moduleFragment), expression)
              ?: super.visitErrorExpression(expression, data)
          }
      },
      Unit
    )
  }

  fun Meta.irErrorCallExpression(
    f: IrUtils.(IrErrorCallExpression) -> IrExpression?
  ): IRGeneration = IrGeneration { compilerContext, moduleFragment, pluginContext ->
    moduleFragment.transformChildren(
      object : IrElementTransformer<Unit> {
        override fun visitErrorCallExpression(
          expression: IrErrorCallExpression,
          data: Unit
        ): IrExpression =
          expression.transformChildren(this, Unit).let {
            f(IrUtils(pluginContext, compilerContext, moduleFragment), expression)
              ?: super.visitErrorCallExpression(expression, data)
          }
      },
      Unit
    )
  }

  fun Meta.irDump(): IRGeneration = IrGeneration { compilerContext, moduleFragment, pluginContext ->
    println(moduleFragment.dump())
  }

  fun Meta.irDumpKotlinLike(
    options: KotlinLikeDumpOptions = KotlinLikeDumpOptions()
  ): IRGeneration = IrGeneration { compilerContext, moduleFragment, pluginContext ->
    println(moduleFragment.dumpKotlinLike(options))
  }
}
