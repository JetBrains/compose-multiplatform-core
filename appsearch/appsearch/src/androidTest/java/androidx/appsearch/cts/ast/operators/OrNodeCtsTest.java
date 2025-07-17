/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.appsearch.cts.ast.operators;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import androidx.appsearch.ast.NegationNode;
import androidx.appsearch.ast.Node;
import androidx.appsearch.ast.TextNode;
import androidx.appsearch.ast.operators.AndNode;
import androidx.appsearch.ast.operators.OrNode;
import androidx.appsearch.flags.Flags;
import androidx.appsearch.testutil.AppSearchTestUtils;
import androidx.appsearch.testutil.flags.RequiresFlagsEnabled;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import java.util.Collections;
import java.util.List;

@RequiresFlagsEnabled(Flags.FLAG_ENABLE_ABSTRACT_SYNTAX_TREES)
public class OrNodeCtsTest {
    @Rule
    public final RuleChain mRuleChain = AppSearchTestUtils.createCommonTestRules();

    @Test
    public void testEquals_identical() {
        TextNode foo = new TextNode("foo");
        TextNode bar = new TextNode("bar");
        OrNode orNodeOne = new OrNode(List.of(foo, bar));

        TextNode fooTwo = new TextNode("foo");
        TextNode barTwo = new TextNode("bar");
        OrNode orNodeTwo = new OrNode(List.of(fooTwo, barTwo));

        assertThat(orNodeOne).isEqualTo(orNodeTwo);
        assertThat(orNodeOne.hashCode()).isEqualTo(orNodeTwo.hashCode());
    }

    @Test
    public void testConstructor_buildsOrNode() {
        TextNode foo = new TextNode("foo");
        TextNode bar = new TextNode("bar");
        TextNode baz = new TextNode("baz");

        List<Node> childNodes = List.of(foo, bar , baz);

        OrNode orNode = new OrNode(childNodes);
        assertThat(orNode.getChildren()).containsExactly(foo, bar, baz).inOrder();
    }

    @Test
    public void testConstructor_buildsOrNodeVarArgs() {
        TextNode foo = new TextNode("foo");
        TextNode bar = new TextNode("bar");
        TextNode baz = new TextNode("baz");

        OrNode orNode = new OrNode(foo, bar, baz);
        assertThat(orNode.getChildren()).containsExactly(foo, bar, baz).inOrder();
    }

    @Test
    public void testConstructor_throwsWhenNullListPassed() {
        assertThrows(NullPointerException.class, () -> new OrNode(null));
    }

    @Test
    public void testConstructor_throwsWhenNotEnoughNodes() {
        TextNode foo = new TextNode("foo");
        assertThrows(IllegalArgumentException.class,
                () -> new OrNode(Collections.singletonList(foo)));
    }

    @Test
    public void testConstructor_throwsWhenNullArgPassed() {
        TextNode foo = new TextNode("foo");
        TextNode baz = new TextNode("baz");
        assertThrows(NullPointerException.class,
                () -> new OrNode(foo, null, baz));
    }

    @Test
    public void testSetChildren_throwsWhenNullListPassed() {
        TextNode foo = new TextNode("foo");
        TextNode bar = new TextNode("bar");
        OrNode orNode = new OrNode(foo, bar);

        assertThrows(NullPointerException.class, () -> orNode.setChildren(null));
    }

    @Test
    public void testSetChildren_throwsWhenNotEnoughNodes() {
        TextNode foo = new TextNode("foo");
        TextNode bar = new TextNode("bar");
        OrNode orNode = new OrNode(foo, bar);

        assertThrows(IllegalArgumentException.class,
                () -> orNode.setChildren(Collections.singletonList(new TextNode("baz"))));
    }

    @Test
    public void testAddChild_addsToBackOfList() {
        TextNode foo = new TextNode("foo");
        TextNode bar = new TextNode("bar");
        OrNode orNode = new OrNode(foo, bar);

        TextNode baz = new TextNode("baz");
        orNode.addChild(baz);

        assertThat(orNode.getChildren()).containsExactly(foo, bar, baz).inOrder();
    }

    @Test
    public void testSetChild_throwsOnOutOfRangeIndex() {
        TextNode foo = new TextNode("foo");
        TextNode bar = new TextNode("bar");
        OrNode orNode = new OrNode(foo, bar);

        TextNode baz = new TextNode("baz");
        assertThrows(IllegalArgumentException.class, () -> orNode.setChild(3, baz));
    }

    @Test
    public void testSetChild_throwsOnNullNode() {
        TextNode foo = new TextNode("foo");
        TextNode bar = new TextNode("bar");
        OrNode orNode = new OrNode(foo, bar);

        assertThrows(NullPointerException.class, () -> orNode.setChild(0, null));
    }

    @Test
    public void testSetChild_setCorrectChild() {
        TextNode foo = new TextNode("foo");
        TextNode bar = new TextNode("bar");
        OrNode orNode = new OrNode(foo, bar);

        TextNode baz = new TextNode("baz");
        orNode.setChild(0, baz);
        assertThat(orNode.getChildren()).containsExactly(baz, bar).inOrder();
    }

    @Test
    public void testGetChildren_returnsCopy() {
        TextNode foo = new TextNode("foo");
        TextNode bar = new TextNode("bar");
        OrNode orNode = new OrNode(foo, bar);

        List<Node> copyChildNodes = orNode.getChildren();

        // Check that initially copy is equivalent to original list
        assertThat(copyChildNodes).containsExactly(foo, bar).inOrder();
        // Now make changes to the original list
        TextNode baz = new TextNode("baz");
        orNode.setChild(1, baz);

        TextNode bat = new TextNode("bat");
        orNode.addChild(bat);
        // Check that the copied list is the same as the original list.
        assertThat(copyChildNodes).containsExactly(foo, baz, bat).inOrder();
    }

    @Test
    public void testGetIndexOfChild_throwsOnNull() {
        TextNode foo = new TextNode("foo");
        TextNode bar = new TextNode("bar");
        OrNode orNode = new OrNode(foo, bar);

        assertThrows(NullPointerException.class, () -> orNode.getIndexOfChild(null));
    }

    @Test
    public void testGetIndexOfChild_nodeExists_returnsIndex() {
        TextNode foo = new TextNode("foo");
        TextNode bar = new TextNode("bar");
        OrNode orNode = new OrNode(foo, bar);

        TextNode nodeToFind = new TextNode("bar");
        assertThat(orNode.getIndexOfChild(nodeToFind)).isEqualTo(1);
    }

    @Test
    public void testGetIndexOfChild_nodeDoesNotExist_returnsNegativeOne() {
        TextNode foo = new TextNode("foo");
        TextNode bar = new TextNode("bar");
        OrNode orNode = new OrNode(foo, bar);

        TextNode nodeToFind = new TextNode("baz");
        assertThat(orNode.getIndexOfChild(nodeToFind)).isEqualTo(-1);
    }

    @Test
    public void testGetIndexOfChild_multipleCopiesExist_returnsIndexOfFirst() {
        TextNode fooOne = new TextNode("foo");
        TextNode fooTwo = new TextNode("foo");
        OrNode orNode = new OrNode(fooOne, fooTwo);

        TextNode nodeToFind = new TextNode("foo");
        assertThat(orNode.getIndexOfChild(nodeToFind)).isEqualTo(0);
    }

    @Test
    public void testRemoveChild_throwsIfListIsTooSmall() {
        TextNode foo = new TextNode("foo");
        TextNode bar = new TextNode("bar");
        OrNode orNode = new OrNode(foo, bar);

        assertThrows(IllegalStateException.class, () -> orNode.removeChild(new TextNode("foo")));
    }

    @Test
    public void testRemoveChild_throwsOnNull() {
        TextNode foo = new TextNode("foo");
        TextNode bar = new TextNode("bar");
        TextNode baz = new TextNode("baz");
        OrNode orNode = new OrNode(foo, bar, baz);

        assertThrows(NullPointerException.class, () -> orNode.removeChild(null));
    }

    @Test
    public void testRemoveChild_nonExistentNode_listUnchanged() {
        TextNode foo = new TextNode("foo");
        TextNode bar = new TextNode("bar");
        TextNode baz = new TextNode("baz");
        OrNode orNode = new OrNode(foo, bar, baz);

        assertThat(orNode.removeChild(new TextNode("bat"))).isFalse();
        assertThat(orNode.getChildren()).containsExactly(foo, bar, baz).inOrder();
    }

    @Test
    public void testRemoveChild_removesNode() {
        TextNode foo = new TextNode("foo");
        TextNode bar = new TextNode("bar");
        TextNode baz = new TextNode("baz");
        OrNode orNode = new OrNode(foo, bar, baz);

        assertThat(orNode.removeChild(baz)).isTrue();
        assertThat(orNode.getChildren()).containsExactly(foo, bar).inOrder();
    }

    @Test
    public void testRemoveChild_multipleCopies_removesFirstCopy() {
        TextNode foo = new TextNode("foo");
        TextNode barOne = new TextNode("bar");
        TextNode barTwo = new TextNode("bar");
        OrNode orNode = new OrNode(barOne, foo, barTwo);

        assertThat(orNode.removeChild(new TextNode("bar"))).isTrue();
        assertThat(orNode.getChildren()).containsExactly(foo, barTwo).inOrder();
    }

    @Test
    public void testToString_joinsChildNodesWithOr() {
        TextNode foo = new TextNode("foo");
        TextNode bar = new TextNode("bar");
        TextNode baz = new TextNode("baz");
        NegationNode notBaz = new NegationNode(baz);

        OrNode orNode = new OrNode(foo, bar, notBaz);
        assertThat(orNode.toString()).isEqualTo("((foo) OR (bar) OR NOT (baz))");
    }

    @Test
    public void testToString_respectsOperatorPrecedence() {
        TextNode foo = new TextNode("foo");
        TextNode bar = new TextNode("bar");
        TextNode baz = new TextNode("baz");
        AndNode andNode = new AndNode(foo, bar);

        OrNode orNode = new OrNode(andNode, baz);
        assertThat(orNode.toString()).isEqualTo("(((foo) AND (bar)) OR (baz))");
    }
}
