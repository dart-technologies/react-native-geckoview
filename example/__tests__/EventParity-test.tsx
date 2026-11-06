/**
 * @format
 * Event registration parity
 *
 * The codegen spec and the Kotlin view manager must list the same view
 * events. On Fabric, React Native builds the component's view config from
 * the spec, so an event the spec leaves out is dropped without a warning,
 * and every other suite stays green: index.tsx casts the spec to the
 * public props type.
 */
import { readFileSync } from 'fs';
import { join, resolve } from 'path';

const root = resolve(__dirname, '..', '..');
const read = (file: string) => readFileSync(join(root, file), 'utf8');

function eventNames(source: string, pattern: RegExp, pick: (match: RegExpExecArray) => string): string[] {
    const names: string[] = [];
    for (let match = pattern.exec(source); match !== null; match = pattern.exec(source)) {
        names.push(pick(match));
    }
    return names.sort();
}

test('the codegen spec declares exactly the events the Kotlin view manager registers', () => {
    const spec = eventNames(
        read('src/GeckoViewNativeComponent.ts'),
        /(on\w+)\?: CodegenTypes\.DirectEventHandler/g,
        (match) => match[1],
    );
    const kotlin = eventNames(
        read('android/src/newarch/kotlin/com/reactnative/geckoview/GeckoViewManager.kt'),
        /"top(\w+)" to mapOf\("registrationName" to "(on\w+)"\)/g,
        (match) => {
            // sendViewEvent dispatches onX as topX.
            expect(match[2]).toBe(`on${match[1]}`);
            return match[2];
        },
    );

    expect(spec).toContain('onContentProcessGone');
    expect(spec).toEqual(kotlin);
});
