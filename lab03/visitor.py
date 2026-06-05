import tkinter as tk
from tkinter import messagebox
from dataclasses import dataclass
from enum import Enum, auto
from typing import Any

# Лексер 
class TokenType(Enum):
    NUMBER = auto()
    OPERATOR = auto()
    LPAREN = auto()
    RPAREN = auto()
    EOF = auto()

@dataclass
class Token:
    type: TokenType
    value: Any
    pos: int

class Lexer:
    def __init__(self, text: str):
        self.text = text
        self.pos = 0
        self.current_char = text[0] if text else None

    def advance(self):
        self.pos += 1
        self.current_char = self.text[self.pos] if self.pos < len(self.text) else None

    def skip_whitespace(self):
        while self.current_char is not None and self.current_char.isspace():
            self.advance()

    def number(self) -> Token:
        start = self.pos
        while self.current_char is not None and self.current_char.isdigit():
            self.advance()
        num_str = self.text[start:self.pos]
        return Token(TokenType.NUMBER, int(num_str), start)

    def get_next_token(self) -> Token:
        while self.current_char is not None:
            if self.current_char.isspace():
                self.skip_whitespace()
                continue
            if self.current_char.isdigit():
                return self.number()
            if self.current_char == '(':
                pos = self.pos
                self.advance()
                return Token(TokenType.LPAREN, '(', pos)
            if self.current_char == ')':
                pos = self.pos
                self.advance()
                return Token(TokenType.RPAREN, ')', pos)
            if self.current_char in '+-*/^':
                op = self.current_char
                pos = self.pos
                self.advance()
                return Token(TokenType.OPERATOR, op, pos)
            raise SyntaxError(f"Неожиданный символ '{self.current_char}' на позиции {self.pos}")
        return Token(TokenType.EOF, None, self.pos)

# Узлы
@dataclass
class AstNode:
    def accept(self, visitor: 'AstVisitor'):
        raise NotImplementedError

@dataclass
class NumberNode(AstNode):
    value: int

    def accept(self, visitor: 'AstVisitor'):
        return visitor.visit_number(self)

@dataclass
class BinaryOpNode(AstNode):
    left: AstNode
    op: str
    right: AstNode

    def accept(self, visitor: 'AstVisitor'):
        return visitor.visit_binary_op(self)

# Базовый Visitor
class AstVisitor:
    def visit_number(self, node: NumberNode):
        pass

    def visit_binary_op(self, node: BinaryOpNode):
        pass

# Конкретные Visitor'ы 
class PrintVisitor(AstVisitor):
    "Преобразует AST в строку с полными скобками."
    def visit_number(self, node: NumberNode) -> str:
        return str(node.value)

    def visit_binary_op(self, node: BinaryOpNode) -> str:
        left_str = node.left.accept(self)
        right_str = node.right.accept(self)
        return f"({left_str} {node.op} {right_str})"

class RPNVisitor(AstVisitor):
    "Генерирует ОПС."
    def visit_number(self, node: NumberNode) -> str:
        return str(node.value)

    def visit_binary_op(self, node: BinaryOpNode) -> str:
        left_str = node.left.accept(self)
        right_str = node.right.accept(self)
        return f"{left_str} {right_str} {node.op}"

class EvaluatorVisitor(AstVisitor):
    "Вычисляет значение выражения."
    def visit_number(self, node: NumberNode) -> float:
        return node.value

    def visit_binary_op(self, node: BinaryOpNode) -> float:
        left_val = node.left.accept(self)
        right_val = node.right.accept(self)

        if node.op == '+':
            return left_val + right_val
        elif node.op == '-':
            return left_val - right_val
        elif node.op == '*':
            return left_val * right_val
        elif node.op == '/':
            if right_val == 0:
                raise ZeroDivisionError("Деление на ноль")
            return left_val / right_val
        elif node.op == '^':
            return left_val ** right_val
        else:
            raise ValueError(f"Неизвестный оператор {node.op}")

# Парсер 
class Parser:
    def __init__(self, tokens: list[Token]):
        self.tokens = tokens
        self.pos = 0
        self.current_token = tokens[0] if tokens else None

    def eat(self, token_type: TokenType):
        if self.current_token is None:
            raise SyntaxError("Неожиданный конец ввода")
        if self.current_token.type == token_type:
            self.pos += 1
            self.current_token = self.tokens[self.pos] if self.pos < len(self.tokens) else None
        else:
            raise SyntaxError(f"Ожидался {token_type}, получен {self.current_token.type} на позиции {self.current_token.pos}")

    def parse(self) -> AstNode:
        node = self.expr()
        if self.current_token is not None and self.current_token.type != TokenType.EOF:
            raise SyntaxError(f"Лишние токены после выражения: {self.current_token.type}")
        return node

    def expr(self) -> AstNode:
        node = self.term()
        while self.current_token is not None and self.current_token.type == TokenType.OPERATOR and self.current_token.value in ('+', '-'):
            op = self.current_token.value
            self.eat(TokenType.OPERATOR)
            right = self.term()
            node = BinaryOpNode(node, op, right)
        return node

    def term(self) -> AstNode:
        node = self.power()
        while self.current_token is not None and self.current_token.type == TokenType.OPERATOR and self.current_token.value in ('*', '/'):
            op = self.current_token.value
            self.eat(TokenType.OPERATOR)
            right = self.power()
            node = BinaryOpNode(node, op, right)
        return node

    def power(self) -> AstNode:
        node = self.primary()
        if self.current_token is not None and self.current_token.type == TokenType.OPERATOR and self.current_token.value == '^':
            self.eat(TokenType.OPERATOR)
            right = self.power()
            node = BinaryOpNode(node, '^', right)
        return node

    def primary(self) -> AstNode:
        token = self.current_token
        if token is None:
            raise SyntaxError("Неожиданный конец ввода")
        if token.type == TokenType.NUMBER:
            self.eat(TokenType.NUMBER)
            return NumberNode(token.value)
        elif token.type == TokenType.LPAREN:
            self.eat(TokenType.LPAREN)
            node = self.expr()
            self.eat(TokenType.RPAREN)
            return node
        else:
            raise SyntaxError(f"Неожиданный токен {token.type} на позиции {token.pos}")

#  Функции обработки выражения 
def parse_expression(expression: str) -> AstNode:
    """Лексический анализ + синтаксический анализ. Возвращает AST."""
    lexer = Lexer(expression)
    tokens = []
    while True:
        tok = lexer.get_next_token()
        tokens.append(tok)
        if tok.type == TokenType.EOF:
            break
    parser = Parser(tokens)
    return parser.parse()

def evaluate(expression: str) -> str:
    try:
        ast = parse_expression(expression)
        result = ast.accept(EvaluatorVisitor())
        return str(result)
    except Exception as e:
        return f"Ошибка: {e}"

def show_ast(expression: str) -> str:
    try:
        ast = parse_expression(expression)
        visitor = PrintVisitor()
        return ast.accept(visitor)
    except Exception as e:
        return f"Ошибка: {e}"

def show_rpn(expression: str) -> str:
    try:
        ast = parse_expression(expression)
        visitor = RPNVisitor()
        return ast.accept(visitor)
    except Exception as e:
        return f"Ошибка: {e}"

#  GUI 
WINDOW_BG = "#2C3E50"
LABEL_FG  = "white"
ENTRY_BG  = "white"
ENTRY_FG  = "black"

STYLES = {
    "label": {
        "bg": WINDOW_BG,
        "fg": LABEL_FG,
        "font": ("Arial", 14)
    },
    "entry": {
        "bg": ENTRY_BG,
        "fg": ENTRY_FG,
        "font": ("Arial", 14),
        "width": 50
    },
    "button": {
        "bg": "#3498DB",
        "fg": "white",
        "activebackground": "#2980B9",
        "activeforeground": "white",
        "font": ("Arial", 12),
        "relief": tk.RAISED,
        "borderwidth": 2,
        "padx": 15,
        "pady": 8,
        "cursor": "hand2"
    },
    "result_label": {
        "bg": WINDOW_BG,
        "fg": "yellow",
        "font": ("Arial", 12)
    }
}

def on_calculate():
    expr = entry.get().strip()
    if not expr:
        messagebox.showwarning("Предупреждение", "Введите выражение")
        return
    res = evaluate(expr)
    result_label.config(text=f"Результат: {res}")

def on_show_ast():
    expr = entry.get().strip()
    if not expr:
        messagebox.showwarning("Предупреждение", "Введите выражение")
        return
    ast_str = show_ast(expr)
    result_label.config(text=f"AST: {ast_str}")

def on_show_rpn():
    expr = entry.get().strip()
    if not expr:
        messagebox.showwarning("Предупреждение", "Введите выражение")
        return
    rpn_str = show_rpn(expr)
    result_label.config(text=f"ОПС: {rpn_str}")

# Создание окна
window = tk.Tk()
window.title("Laba3")
window.geometry("800x500")
window.configure(bg=WINDOW_BG)

label = tk.Label(window, text="Введите выражение (цифры, + - * / ^, скобки)", **STYLES["label"])
label.pack(pady=40)

entry = tk.Entry(window, **STYLES["entry"])
entry.pack(pady=10)

button_frame = tk.Frame(window, bg=WINDOW_BG)
button_frame.pack(pady=20)

btn_calc = tk.Button(button_frame, text="Вычислить", command=on_calculate, **STYLES["button"])
btn_calc.pack(side='left', padx=10)

btn_ast = tk.Button(button_frame, text="AST", command=on_show_ast, **STYLES["button"])
btn_ast.pack(side='left', padx=10)

btn_ops = tk.Button(button_frame, text="ОПС", command=on_show_rpn, **STYLES["button"])
btn_ops.pack(side='left', padx=10)

# Метка для вывода результата
result_label = tk.Label(window, text="", **STYLES["result_label"])
result_label.pack(pady=30)

window.mainloop()
