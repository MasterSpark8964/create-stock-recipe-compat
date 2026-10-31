"""Compare class/member contracts and packaged resources with the original Jar.
This checks restoration completeness, not full behavioral equivalence.
"""
import json
import struct
import sys
import zipfile

class ClassFile:
    def __init__(self, data):
        self.data, self.pos = data, 0
        assert self.u4() == 0xCAFEBABE
        self.u2(); self.u2()
        count = self.u2()
        self.cp = [None] * count
        i = 1
        while i < count:
            tag = self.u1()
            if tag == 1:
                size = self.u2(); self.cp[i] = self.take(size).decode('utf-8', 'replace')
            elif tag in (3, 4): self.cp[i] = (tag, self.take(4).hex())
            elif tag in (5, 6): self.cp[i] = (tag, self.take(8).hex()); i += 1
            elif tag in (7, 8, 16, 19, 20): self.cp[i] = (tag, self.u2())
            elif tag in (9, 10, 11, 12, 17, 18): self.cp[i] = (tag, self.u2(), self.u2())
            elif tag == 15: self.cp[i] = (tag, self.u1(), self.u2())
            else: raise ValueError(tag)
            i += 1
        self.access = self.u2()
        self.name = self.class_name(self.u2())
        self.parent = self.class_name(self.u2())
        self.interfaces = sorted(self.class_name(self.u2()) for _ in range(self.u2()))
        self.fields = self.members()
        self.methods = self.members()
    def take(self, n):
        value = self.data[self.pos:self.pos+n]; self.pos += n
        return value
    def u1(self): return self.take(1)[0]
    def u2(self): return struct.unpack('>H', self.take(2))[0]
    def u4(self): return struct.unpack('>I', self.take(4))[0]
    def class_name(self, index): return self.cp[self.cp[index][1]] if index else None
    def constant(self, index):
        value = self.cp[index]
        return self.cp[value[1]] if isinstance(value, tuple) and value[0] == 8 else value
    def members(self):
        result = {}
        for _ in range(self.u2()):
            access, name, desc = self.u2(), self.cp[self.u2()], self.cp[self.u2()]
            const = None
            for _ in range(self.u2()):
                attr, data = self.cp[self.u2()], self.take(self.u4())
                if attr == 'ConstantValue': const = self.constant(struct.unpack('>H', data)[0])
            # javac may assign different lambda/anonymous capture names; exclude synthetic members.
            if not access & 0x1000: result[name + desc] = (access, const)
        return result

def main(original, rebuilt):
    with zipfile.ZipFile(original) as before, zipfile.ZipFile(rebuilt) as after:
        classes = {n for n in before.namelist() if n.endswith('.class')}
        actual = {n for n in after.namelist() if n.endswith('.class')}
        errors = []
        if classes != actual: errors.append({'classes_missing': sorted(classes-actual), 'classes_added': sorted(actual-classes)})
        methods = fields = 0
        for name in sorted(classes & actual):
            a, b = ClassFile(before.read(name)), ClassFile(after.read(name))
            methods += len(a.methods); fields += len(a.fields)
            for kind in ('name', 'parent', 'interfaces', 'access', 'fields', 'methods'):
                if getattr(a, kind) != getattr(b, kind):
                    errors.append({'class': name, 'contract': kind, 'before': getattr(a, kind), 'after': getattr(b, kind)})
        resources = [n for n in before.namelist() if not n.endswith(('/', '.class')) and n != 'META-INF/MANIFEST.MF']
        for name in resources:
            if name not in after.namelist(): errors.append({'resource_missing': name}); continue
            a, b = before.read(name), after.read(name)
            equal = json.loads(a) == json.loads(b) if name.endswith('.json') else a == b
            if not equal: errors.append({'resource_differs': name})
        print(json.dumps({'classes_checked': len(classes), 'methods_checked': methods, 'fields_checked': fields, 'resources_checked': len(resources), 'differences': errors}, indent=2, ensure_ascii=False))
        return bool(errors)

if __name__ == '__main__': sys.exit(main(*sys.argv[1:]))
