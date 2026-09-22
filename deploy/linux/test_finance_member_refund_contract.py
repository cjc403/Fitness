import ast
import pathlib
import unittest


SOURCE = pathlib.Path(__file__).with_name("refund-finance-acceptance.py")


def load_contract():
    tree = ast.parse(SOURCE.read_text(encoding="utf-8"))
    selected = [node for node in tree.body
                if isinstance(node, ast.Assign) and any(getattr(t, "id", None) == "MEMBER_REFUND_FIELDS" for t in node.targets)]
    selected += [node for node in tree.body
                 if isinstance(node, ast.FunctionDef) and node.name == "assert_member_refund"]
    namespace = {}
    exec(compile(ast.Module(body=selected, type_ignores=[]), str(SOURCE), "exec"), namespace)
    return namespace["assert_member_refund"]


class MemberRefundContractTest(unittest.TestCase):
    def test_accepts_exact_public_fields_including_null_optional_dates(self):
        validate = load_contract()
        validate({'id': 1, 'refundNo': 'R1', 'entitlementNo': 'E1', 'status': 'PENDING',
                  'amount': 10, 'requestedAt': 'now', 'approvedAt': None, 'paidAt': None})

    def test_rejects_missing_or_internal_fields(self):
        validate = load_contract()
        base = {'id': 1, 'refundNo': 'R1', 'entitlementNo': 'E1', 'status': 'PENDING',
                'amount': 10, 'requestedAt': 'now', 'approvedAt': None, 'paidAt': None}
        for changed in ({key: value for key, value in base.items() if key != 'paidAt'},
                        dict(base, externalReference='secret')):
            with self.assertRaises(AssertionError):
                validate(changed)


if __name__ == "__main__":
    unittest.main()
