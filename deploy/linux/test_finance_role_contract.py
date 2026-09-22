import ast
import datetime as dt
import pathlib
import unittest

SOURCE = pathlib.Path(__file__).with_name('refund-finance-acceptance.py')

class FinanceRoleContractTest(unittest.TestCase):
    def test_readonly_role_matrix_accepts_reception_scoped_reports(self):
        tree = ast.parse(SOURCE.read_text(encoding='utf-8'))
        begin = next(i for i, n in enumerate(tree.body) if isinstance(n, ast.Assign) and any(getattr(t, 'id', '') == 'today' for t in n.targets))
        end = next(i for i in range(begin, len(tree.body)) if isinstance(tree.body[i], ast.If) and isinstance(tree.body[i].test, ast.Attribute) and tree.body[i].test.attr == 'readonly')
        calls = []
        def request(path, who='admin', **kwargs):
            actual_allowed = who in ('admin', 'manager', 'reception') or path == '/finance/me/refunds' and who == 'member'
            self.assertEqual(kwargs.get('expected_http', 200), 200 if actual_allowed else 403, (who, path))
            calls.append((who, path))
            return [] if actual_allowed else {'code': 1003}
        namespace = {'dt': dt, 'identity': lambda who: {'must_change_password': False}, 'request': request, 'assert_member_refund': lambda record: None}
        exec(compile(ast.Module(body=tree.body[begin:end], type_ignores=[]), str(SOURCE), 'exec'), namespace)
        for who in ('admin', 'manager', 'reception', 'coach', 'member'):
            self.assertTrue(any(role == who and path.startswith('/finance/report?') for role, path in calls))

if __name__ == '__main__':
    unittest.main()
